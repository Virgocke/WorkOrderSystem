package com.WorkOrder.search.model;

import lombok.Getter;
import lombok.EqualsAndHashCode;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 目录搜索投影仅保存可搜索字段，用户 ID 与处理人档案 ID 严格区分，不读取密码。
 */
@Getter
@Setter
@EqualsAndHashCode
public class DirectorySearchDocument {
    private Long userId;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private List<String> skillNames = new ArrayList<>();
}
