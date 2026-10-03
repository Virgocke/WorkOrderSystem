package com.WorkOrder.handler.mapper;

import com.WorkOrder.handler.dto.HandlerSkillRequest;
import com.WorkOrder.handler.model.HandlerProfiles;
import com.WorkOrder.model.handler.HandlerProfile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * @author Virgor
 * @date 2026年09月16日 02:39
 * @description 处理人Mapper
 */
@Mapper
public interface HandlerProfileMapper extends BaseMapper<HandlerProfiles> {

    /**
     * 单次联表查询启用处理人，用户主键用于分配，档案主键不对外作为用户 ID。
     * @return 启用处理人列表
     */
    List<HandlerProfile> selectEnabledHandlerOptions();

    /**
     * 按 ES 候选用户 ID 分页查询处理人档案；null 不限制 ID，空集合不返回记录。
     */
    Page<HandlerProfile> selectHandlerPage(Page<HandlerProfile> page,
                                           @Param("matchedUserIds") List<Long> matchedUserIds,
                                           @Param("status") Integer status);

    /**
     * 查询全部处理人档案，包含停用账号。
     */
    List<HandlerProfile> selectAllHandlerProfiles();

    /** 按用户 ID 查询单个处理人档案及其技能。 */
    HandlerProfile selectHandlerProfileByUserId(@Param("userId") Long userId);

    /** 查询部门是否存在。 */
    int countDepartmentById(@Param("departmentId") Long departmentId);

    /** 按用户 ID 更新处理人最大容量。 */
    int updateHandlerCapacity(@Param("userId") Long userId,
                              @Param("maxCapacity") Integer maxCapacity);

    /** 按用户 ID 部分更新处理人的部门和账号状态。 */
    int updateHandlerUser(@Param("userId") Long userId,
                          @Param("departmentId") Long departmentId,
                          @Param("departmentIdPresent") boolean departmentIdPresent,
                          @Param("status") Integer status,
                          @Param("statusPresent") boolean statusPresent);

    /** 统计给定技能 ID 中实际存在的标签数量。 */
    int countSkillTagsByIds(@Param("skillIds") Set<Long> skillIds);

    /** 按处理人的用户 ID 删除其全部技能。 */
    int deleteHandlerSkillsByUserId(@Param("userId") Long userId);

    /** 按处理人的用户 ID 批量新增技能。 */
    int insertHandlerSkills(@Param("userId") Long userId,
                            @Param("skills") List<HandlerSkillRequest> skills);
}
