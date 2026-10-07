package com.WorkOrder.notification.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 持久化邮件任务；与 EMAIL 通知一对一，SMTP 在业务事务提交后执行。
 */
@Data
@TableName("notification_email_deliveries")
public class EmailDelivery {
    /**
     * 数据库生成的任务主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 关联 EMAIL 通知 ID，唯一约束防止重复创建任务。
     */
    private Long notificationId;
    /**
     * 单个收件邮箱快照；不可用时为空且任务直接失败。
     */
    private String recipient;
    /**
     * 邮件主题快照。
     */
    private String subject;
    /**
     * 纯文本正文快照。
     */
    private String content;
    /**
     * PENDING、SENDING、RETRY、SENT 或 FAILED。
     */
    private String status;
    /**
     * 管理员重试轮次；初次投递为零。
     */
    private Integer retryRound;
    /**
     * 当前轮已领取次数，包含领取后进程退出的尝试。
     */
    private Integer attempts;
    /**
     * 所有轮次累计领取次数，手工重试时不重置。
     */
    private Long totalAttempts;
    /**
     * 最早允许重试的本地时间。
     */
    private LocalDateTime nextAttemptAt;
    /**
     * 领取租约到期时间，租约为五分钟。
     */
    private LocalDateTime leaseUntil;
    /**
     * 每次领取生成的令牌，防止过期工作者覆盖新状态。
     */
    private String claimToken;
    /**
     * 脱敏失败原因，不记录 SMTP 凭据、完整异常或收件邮箱。
     */
    private String lastError;
    /**
     * SMTP 服务接受邮件的时间，不表示用户已阅读。
     */
    private LocalDateTime sentAt;
    /**
     * 任务创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近状态变更时间。
     */
    private LocalDateTime updatedAt;
}
