package com.WorkOrder.skill.service.impl;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.Result;
import com.WorkOrder.model.handler.SkillApplication;
import com.WorkOrder.model.handler.SkillTag;
import com.WorkOrder.skill.dto.SkillApplicationDto;
import com.WorkOrder.skill.mapper.SkillApplicationMapper;
import com.WorkOrder.skill.mapper.SkillTagMapper;
import com.WorkOrder.skill.model.SkillApplicationRecord;
import com.WorkOrder.skill.model.SkillTagRecord;
import com.WorkOrder.skill.service.SkillApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

/**
 * @author Virgor
 * @date 2026年09月23日 17:38
 * @description 技能申请服务实现
 */
@Service
@RequiredArgsConstructor
public class SkillApplicationServiceImpl implements SkillApplicationService {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SkillApplicationMapper skillApplicationMapper;
    private final SkillTagMapper skillTagMapper;

    /**
     * 处理技能申请，包括添加或移除技能申请
     * @param skillApplicationDto 技能申请dto
     * @param handlerId 处理人id
     * @return 处理结果
     */
    @Override
    @Transactional
    public Result<SkillApplication> handlerSkillApplication(SkillApplicationDto skillApplicationDto, Long handlerId) {
        // 验证参数
        if (!"REMOVE".equals(skillApplicationDto.getType()) && skillApplicationDto.getProficiency() == 0) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        SkillApplicationRecord skillApplicationRecord = new SkillApplicationRecord();
        skillApplicationRecord.setSkillTagId(skillApplicationDto.getSkillId());
        skillApplicationRecord.setHandlerId(handlerId);
        if ("REMOVE".equals(skillApplicationDto.getType())) {
            skillApplicationRecord.setProficiency(null);
        } else {
            skillApplicationRecord.setProficiency(skillApplicationDto.getProficiency());
        }
        skillApplicationRecord.setType(skillApplicationDto.getType());
        skillApplicationRecord.setReason(skillApplicationDto.getReason());

        int insert = skillApplicationMapper.insert(skillApplicationRecord);
        if (insert < 1) {
            throw new SystemException(SystemExceptionEnum.APPLICATION_FAILED);
        }

        SkillApplicationRecord record = skillApplicationMapper.selectById(skillApplicationRecord.getId());

        return Result.success(convertToSkillApplication(record));
    }

    /**
     * 将技能申请记录转换为技能申请对象
     * @param record 技能申请记录
     * @return 技能申请对象
     */
    private SkillApplication convertToSkillApplication(SkillApplicationRecord record) {

        SkillTagRecord skillTag = skillTagMapper.selectById(record.getSkillTagId());

        return SkillApplication.builder()
                .id(record.getId())
                .handlerId(record.getHandlerId())
                .skillId(record.getSkillTagId())
                .skillName(skillTag.getName())
                .proficiency(record.getProficiency())
                .type(record.getType())
                .reason(record.getReason())
                .status(record.getStatus())
                .createdAt(record.getCreatedAt().format(TIME_FORMATTER))
                .build();
    }
}
