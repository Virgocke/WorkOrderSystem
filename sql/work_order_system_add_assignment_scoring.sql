-- 在部署评分代码前执行一次。旧工单按现存处理人补齐归属；历史上多次转派的
-- 实际解决人若已无法从工单确认，需要业务方核对这些旧记录。
CREATE TABLE IF NOT EXISTS `ticket_category_skills` (
    `category_id` BIGINT NOT NULL,
    `skill_tag_id` BIGINT NOT NULL,
    PRIMARY KEY (`category_id`, `skill_tag_id`),
    KEY `idx_category_skills_tag` (`skill_tag_id`),
    CONSTRAINT `fk_category_skills_category` FOREIGN KEY (`category_id`) REFERENCES `ticket_categories` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_category_skills_tag` FOREIGN KEY (`skill_tag_id`) REFERENCES `skill_tags` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 为名称与演示数据一致的现有分类填入初始规则；其他分类由管理员配置。
INSERT IGNORE INTO `ticket_category_skills` (`category_id`, `skill_tag_id`)
SELECT c.`id`, s.`id`
FROM `ticket_categories` c
JOIN `skill_tags` s ON
    (c.`name` = '网络与安全' AND s.`name` IN ('网络', '安全')) OR
    (c.`name` = '硬件设备' AND s.`name` = '硬件维护') OR
    (c.`name` = '软件系统' AND s.`name` IN ('系统运维', '应用开发')) OR
    (c.`name` = '账号与权限' AND s.`name` = '账号权限');

ALTER TABLE `tickets`
    ADD COLUMN `resolved_by_handler_id` BIGINT NULL COMMENT '提交解决时的处理人快照' AFTER `handler_id`,
    ADD KEY `idx_tickets_resolved_handler` (`resolved_by_handler_id`, `resolved_at`);

ALTER TABLE `ticket_ratings`
    ADD COLUMN `handler_id` BIGINT NULL COMMENT '评价归属的解决处理人快照' AFTER `user_id`,
    ADD KEY `idx_ratings_handler` (`handler_id`);

UPDATE `tickets`
SET `resolved_by_handler_id` = `handler_id`
WHERE `resolved_at` IS NOT NULL AND `handler_id` IS NOT NULL;

UPDATE `ticket_ratings` r
JOIN `tickets` t ON t.`id` = r.`ticket_id`
SET r.`handler_id` = t.`resolved_by_handler_id`
WHERE t.`resolved_by_handler_id` IS NOT NULL;

ALTER TABLE `tickets`
    ADD CONSTRAINT `fk_tickets_resolved_handler` FOREIGN KEY (`resolved_by_handler_id`) REFERENCES `users` (`id`);
ALTER TABLE `ticket_ratings`
    ADD CONSTRAINT `fk_ratings_handler` FOREIGN KEY (`handler_id`) REFERENCES `users` (`id`);
