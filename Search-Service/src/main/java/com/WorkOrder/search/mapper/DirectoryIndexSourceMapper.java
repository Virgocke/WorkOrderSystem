package com.WorkOrder.search.mapper;

import com.WorkOrder.search.model.DirectorySearchDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 按用户主键读取搜索字段，技能关联使用处理人档案主键。 */
@Mapper
public interface DirectoryIndexSourceMapper {
    /** 先对用户分页再联接技能，防止一个用户多项技能占用批次或导致技能被截断。 */
    List<DirectorySearchDocument> selectBatch(@Param("afterId") long afterId, @Param("limit") int limit);
}
