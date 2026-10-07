package com.WorkOrder.search.service;

import com.WorkOrder.search.model.DirectorySearchDocument;
import com.WorkOrder.search.repository.DirectorySearchRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 目录搜索入口与同步协调；仅目录内容变化时构建新快照，空快照也会发布以清除已删除用户。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "work-order.elasticsearch", name = "enabled", havingValue = "true")
public class DirectorySearchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectorySearchService.class);
    private final DirectorySearchSourceReader sourceReader;
    private final DirectorySearchRepository repository;
    private List<DirectorySearchDocument> publishedSnapshot;
    private volatile boolean ready;

    /**
     * 同步新增、改名、联系方式、技能及删除；源数据读取结束后才写 ES，失败禁止返回旧结果冒充最新结果。
     */
    @Scheduled(fixedDelayString = "${work-order.elasticsearch.directory-sync-delay-ms:30000}", initialDelayString = "0")
    public synchronized void synchronize() {
        try {
            // 读取源数据快照
            List<DirectorySearchDocument> current = sourceReader.readSnapshot();
            if (!current.equals(publishedSnapshot) || !repository.hasPublishedSnapshot()) {
                repository.publishSnapshot(current);
                publishedSnapshot = current;
            }
            ready = true;
        } catch (Exception exception) {
            ready = false;
            // 不记录异常正文，防止远端错误回显姓名、邮箱或手机号。
            LOGGER.error("用户目录同步失败，将在下轮重试；errorType={}", exception.getClass().getSimpleName());
            return;
        }
        try {
            repository.cleanupRetiredIndices();
        } catch (Exception exception) {
            LOGGER.warn("用户目录旧快照清理失败，将重试；errorType={}", exception.getClass().getSimpleName());
        }
    }

    /**
     * 校验管理员身份及回填状态，返回完整候选用户 ID；业务层负责当前角色、状态及分页。
     *
     * @param keyword 查询关键词
     * @param handlers true 匹配姓名及技能，false 匹配姓名、邮箱及电话
     * @return 完整的候选用户 ID 列表；角色、状态、排序和分页由后续 MySQL 业务查询处理
     * @throws IOException 处理过程中发生IO异常时
     */
    @PreAuthorize("hasRole('ADMIN')")
    public List<Long> search(String keyword, boolean handlers) throws IOException {
        if (!StringUtils.hasText(keyword)) {
            throw new IllegalArgumentException("目录搜索关键词不能为空");
        }
        if (!ready) {
            throw new IllegalStateException("用户目录搜索正在同步或暂不可用");
        }
        return repository.searchIds(keyword.trim(), handlers);
    }
}
