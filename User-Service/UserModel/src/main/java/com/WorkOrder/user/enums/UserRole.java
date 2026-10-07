package com.WorkOrder.user.enums;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 系统内置角色。细粒度授权由后续 RBAC 权限表承载。
 */
public enum UserRole {
    USER(0, "用户"),
    HANDLER(1, "处理人"),
    ADMIN(2, "管理员");
    private final int code;
    private final String permission;
    /**
     * 获取用户角色代码。
     *
     * @return 当前角色对应的整数代码
     */
    public int getCode() {
        return code;
    }
    /**
     * 获取角色权限标识。
     *
     * @return 当前角色对应的权限标识
     */
    public String getPermission() {
        return permission;
    }
    /**
     * 定义用户角色及其权限标识。
     *
     * @param code 用户角色代码
     * @param permission 角色对应的权限标识
     */
    UserRole(int code, String permission) {
        this.code = code;
        this.permission = permission;
    }
}
