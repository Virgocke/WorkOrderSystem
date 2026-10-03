package com.WorkOrder.search.service;

import com.WorkOrder.search.mapper.DirectoryIndexSourceMapper;
import com.WorkOrder.search.model.DirectorySearchDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** 在独立数据库快照中分批读取目录；事务结束后才执行 ES 网络写入。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "work-order.elasticsearch", name = "enabled", havingValue = "true")
public class DirectorySearchSourceReader {
    // 每次读取的批次大小
    private static final int BATCH_SIZE = 500;
    private final DirectoryIndexSourceMapper mapper;

    /** 每轮从零开始覆盖改名、联系方式、技能变更及迟提交记录，不依赖永久 ID 水位。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<DirectorySearchDocument> readSnapshot() {
        List<DirectorySearchDocument> snapshot = new ArrayList<>();
        long afterId = 0;
        while (true) {
            List<DirectorySearchDocument> batch = mapper.selectBatch(afterId, BATCH_SIZE);
            if (batch == null || batch.size() > BATCH_SIZE) {
                throw new IllegalStateException("用户目录同步批次无效");
            }
            for (DirectorySearchDocument document : batch) {
                if (document == null || document.getUserId() == null || document.getUserId() <= afterId) {
                    throw new IllegalStateException("用户目录同步主键必须递增");
                }
                afterId = document.getUserId();
                snapshot.add(document);
            }
            if (batch.size() < BATCH_SIZE) {
                return snapshot;
            }
        }
    }
}
