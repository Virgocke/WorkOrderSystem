package com.WorkOrder.dashboard.mapper;

import com.WorkOrder.dashboard.dto.*;
import com.WorkOrder.model.handler.HandlerProfile;
import com.WorkOrder.model.handler.HandlerSkillItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/** 管理端全局仪表盘统计查询。 */
@Mapper
public interface DashboardMapper {

    /**
     * 获取全局概览数据。
     *
     * <p>返回字段：</p>
     * <ul>
     *     <li>{@code totalTickets}：系统中的工单总数</li>
     *     <li>{@code pendingCount}：待分配与待响应工单数量之和</li>
     *     <li>{@code slaComplianceRate}：已关闭工单的 SLA 达成率</li>
     *     <li>{@code avgResponseMinutes}：从创建到首次响应的平均分钟数</li>
     *     <li>{@code avgResolutionMinutes}：从创建到解决的平均分钟数</li>
     * </ul>
     *
     * @return 全局概览数据
     */
    DashboardSummaryDto selectSummary();

    /**
     * 获取工单状态分组计数。
     *
     * <p>返回元素字段：</p>
     * <ul>
     *     <li>{@code groupKey}：工单状态</li>
     *     <li>{@code count}：该状态下的工单数量</li>
     * </ul>
     *
     * @return 工单状态分组计数
     */
    List<DashboardGroupCountDto> selectStatusCounts();

    /**
     * 获取工单优先级分组计数。
     *
     * <p>返回元素字段：</p>
     * <ul>
     *     <li>{@code groupKey}：工单优先级</li>
     *     <li>{@code count}：该优先级下的工单数量</li>
     * </ul>
     *
     * @return 工单优先级分组计数
     */
    List<DashboardGroupCountDto> selectPriorityCounts();

    /**
     * 获取工单趋势数据。
     *
     * <p>返回元素字段：</p>
     * <ul>
     *     <li>{@code statDate}：趋势统计日期</li>
     *     <li>{@code created}：当日创建的工单数量</li>
     *     <li>{@code resolved}：当日解决的工单数量</li>
     * </ul>
     *
     * @param startDate 统计开始日期（包含）
     * @param endDateExclusive 统计结束日期（不包含）
     * @return 工单趋势数据
     */
    List<DashboardTrendRow> selectTrend(
            @Param("startDate") LocalDate startDate,
            @Param("endDateExclusive") LocalDate endDateExclusive);

    /**
     * 获取工单处理人热力图数据。
     *
     * <p>返回元素字段：</p>
     * <ul>
     *     <li>{@code handlerId}：处理人用户 ID</li>
     *     <li>{@code handlerName}：处理人显示名称</li>
     *     <li>{@code statDate}：负载统计日期</li>
     *     <li>{@code count}：处理人在统计日期的工单数量</li>
     * </ul>
     *
     * @param dates 需要统计的连续日期列表
     * @return 工单处理人热力图数据
     */
    List<DashboardHandlerHeatRow> selectHandlerHeat(@Param("dates") List<LocalDate> dates);

    /**
     * 获取最近工单数据。
     *
     * <p>返回元素字段：</p>
     * <ul>
     *     <li>{@code id}：工单 ID</li>
     *     <li>{@code ticketNo}：工单编号</li>
     *     <li>{@code title}：工单标题</li>
     *     <li>{@code description}：工单详细描述</li>
     *     <li>{@code categoryId}：工单分类 ID</li>
     *     <li>{@code categoryName}：工单分类名称</li>
     *     <li>{@code priority}：工单优先级</li>
     *     <li>{@code status}：工单状态</li>
     *     <li>{@code creatorId}：创建人用户 ID</li>
     *     <li>{@code creatorName}：创建人显示名称</li>
     *     <li>{@code handlerId}：当前处理人用户 ID</li>
     *     <li>{@code handlerName}：当前处理人显示名称</li>
     *     <li>{@code createdAt}：工单创建时间</li>
     *     <li>{@code assignedAt}：工单分配时间</li>
     *     <li>{@code responseDeadline}：首次响应截止时间</li>
     *     <li>{@code resolutionDeadline}：工单解决截止时间</li>
     *     <li>{@code firstResponseAt}：首次响应时间</li>
     *     <li>{@code resolvedAt}：提交解决方案的时间</li>
     *     <li>{@code closedAt}：工单关闭时间</li>
     *     <li>{@code slaStatus}：工单 SLA 状态</li>
     *     <li>{@code escalatedLevel}：工单升级级别</li>
     *     <li>{@code remindCount}：工单催办次数</li>
     *     <li>{@code source}：工单来源</li>
     *     <li>{@code attachmentUrls}：附件 URL JSON 数组文本</li>
     *     <li>{@code updatedAt}：工单最后更新时间</li>
     * </ul>
     *
     * @return 最近工单数据
     */
    List<DashboardTicketRow> selectRecentTickets();

    /**
     * 获取处理人工作台汇总数据。
     *
     * <p>返回字段：</p>
     * <ul>
     *     <li>{@code pendingCount}：待首次响应的工单数量</li>
     *     <li>{@code nearTimeoutCount}：临近超时、已超时或已升级的未终结工单数量</li>
     *     <li>{@code doneToday}：今天解决或关闭的工单数量</li>
     *     <li>{@code avgResolutionMinutes}：从创建到解决的历史平均分钟数</li>
     * </ul>
     *
     * @param handlerId 处理人用户 ID
     * @return 工作台汇总数据
     */
    HandlerWorkbenchSummaryDto selectWorkbenchSummary(@Param("handlerId") Long handlerId);

    /**
     * 获取处理人未终结工单的状态分组计数。
     *
     * <p>返回元素字段：</p>
     * <ul>
     *     <li>{@code groupKey}：未终结工单状态</li>
     *     <li>{@code count}：该状态下的工单数量</li>
     * </ul>
     *
     * @param handlerId 处理人用户 ID
     * @return 状态分组计数
     */
    List<DashboardGroupCountDto> selectWorkbenchStatusCounts(
            @Param("handlerId") Long handlerId);

    /**
     * 获取处理人最接近时限的未终结工单。
     *
     * <p>返回元素字段：</p>
     * <ul>
     *     <li>{@code id}：工单 ID</li>
     *     <li>{@code ticketNo}：工单编号</li>
     *     <li>{@code title}：工单标题</li>
     *     <li>{@code description}：工单详细描述</li>
     *     <li>{@code categoryId}：工单分类 ID</li>
     *     <li>{@code categoryName}：工单分类名称</li>
     *     <li>{@code priority}：工单优先级</li>
     *     <li>{@code status}：工单状态</li>
     *     <li>{@code creatorId}：创建人用户 ID</li>
     *     <li>{@code creatorName}：创建人显示名称</li>
     *     <li>{@code handlerId}：当前处理人用户 ID</li>
     *     <li>{@code handlerName}：当前处理人显示名称</li>
     *     <li>{@code createdAt}：工单创建时间</li>
     *     <li>{@code assignedAt}：工单分配时间</li>
     *     <li>{@code responseDeadline}：首次响应截止时间</li>
     *     <li>{@code resolutionDeadline}：工单解决截止时间</li>
     *     <li>{@code firstResponseAt}：首次响应时间</li>
     *     <li>{@code resolvedAt}：提交解决方案的时间</li>
     *     <li>{@code closedAt}：工单关闭时间</li>
     *     <li>{@code slaStatus}：工单 SLA 状态</li>
     *     <li>{@code escalatedLevel}：工单升级级别</li>
     *     <li>{@code remindCount}：工单催办次数</li>
     *     <li>{@code source}：工单来源</li>
     *     <li>{@code attachmentUrls}：附件 URL JSON 数组文本</li>
     *     <li>{@code updatedAt}：工单最后更新时间</li>
     * </ul>
     *
     * @param handlerId 处理人用户 ID
     * @return 最多八条临期工单
     */
    List<DashboardTicketRow> selectUpcomingTickets(@Param("handlerId") Long handlerId);

    /**
     * 获取处理人档案。
     *
     * <p>返回字段：</p>
     * <ul>
     *     <li>{@code id}：处理人用户 ID</li>
     *     <li>{@code userId}：处理人用户 ID</li>
     *     <li>{@code realName}：处理人姓名</li>
     *     <li>{@code username}：登录账号</li>
     *     <li>{@code departmentId}：所属部门 ID</li>
     *     <li>{@code departmentName}：所属部门名称</li>
     *     <li>{@code maxCapacity}：最大同时处理工单数</li>
     *     <li>{@code currentLoad}：当前在办工单数</li>
     *     <li>{@code avgResponseMinutes}：平均响应分钟数</li>
     *     <li>{@code avgResolutionMinutes}：平均解决分钟数</li>
     *     <li>{@code slaComplianceRate}：SLA 达成率</li>
     *     <li>{@code ratingScore}：平均用户评分</li>
     *     <li>{@code skills}：处理人技能列表；本查询不填充，由技能查询单独获取</li>
     *     <li>{@code status}：处理人状态</li>
     * </ul>
     *
     * @param handlerId 处理人用户 ID
     * @return 处理人档案；不存在时为空
     */
    HandlerProfile selectHandlerProfile(@Param("handlerId") Long handlerId);

    /**
     * 获取处理人的技能列表。
     *
     * <p>返回元素字段：</p>
     * <ul>
     *     <li>{@code skillId}：技能标签 ID</li>
     *     <li>{@code skillName}：技能名称</li>
     *     <li>{@code proficiency}：熟练度，取值范围为 1 至 5</li>
     * </ul>
     *
     * @param handlerId 处理人用户 ID
     * @return 技能列表
     */
    List<HandlerSkillItem> selectHandlerSkills(@Param("handlerId") Long handlerId);

    /**
     * 获取所有启用处理人的实时绩效数据。
     *
     * <p>没有工单或评价数据时，时长、SLA 达成率和评分使用处理人档案中的历史值。</p>
     *
     * @return 处理人绩效列表
     */
    List<HandlerReportPerformanceDto> selectHandlerReportPerformance();

    /**
     * 按顶级分类聚合工单总数和平均解决时长。
     *
     * <p>分类层级不受固定深度限制，仅返回包含工单的顶级分类，
     * 并按工单数降序排列。平均解决时长以分类内全部工单为分母，
     * 未解决或解决时间早于创建时间的工单按零分钟计。</p>
     *
     * @return 顶级分类统计列表
     */
    List<CategoryReportDto> selectCategoryReport();

    /**
     * 按评价时间倒序分页查询评价明细。
     *
     * @param page 分页参数
     * @param handlerId 可选的处理人 ID
     * @return 评价明细分页结果
     */
    Page<RatingDetailDto> selectRatingDetails(
            Page<RatingDetailDto> page,
            @Param("handlerId") Long handlerId);

}
