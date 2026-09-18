package com.WorkOrder.assignment.mapper;

import com.WorkOrder.assignment.model.Configuration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 系统配置及修改记录持久化。 */
@Mapper
public interface ConfigurationMapper {
    /**
     * 查询所有配置。
     * @return 配置列表
     */
    List<Configuration> selectAll();
    /**
     * 根据主键查询配置。
     * @param configKey 配置主键
     * @return 配置
     */
    Configuration selectByKey(@Param("configKey") String configKey);
    /**
     * 插入配置。
     * @param configuration 配置
     * @return 影响行数
     */
    int insert(Configuration configuration);
    /**
     * 根据版本更新配置。
     * @param configuration 配置
     * @return 影响行数
     */
    int updateByVersion(Configuration configuration);
    /**
     * 插入配置修改日志。
     * @param configKey 配置主键
     * @param beforeValue 修改前值
     * @param afterValue 修改后值
     * @param version 版本号
     * @param operatorId 操作员ID
     * @return 影响行数
     */
    int insertLog(@Param("configKey") String configKey,
                  @Param("beforeValue") String beforeValue,
                  @Param("afterValue") String afterValue,
                  @Param("version") Long version,
                  @Param("operatorId") Long operatorId);
}
