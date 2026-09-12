package com.WorkOrder.user.enums;

/**
 * 系统内置角色。细粒度授权由后续 RBAC 权限表承载。
 */
public enum UserRole {
    USER(0, "用户"),
    HANDLER(1, "处理人"),
    ADMIN(2, "管理员");
    private final int code;
    private final String permission;
    public int getCode() {
        return code;
    }
    public String getPermission() {
        return permission;
    }
    UserRole(int code, String permission) {
        this.code = code;
        this.permission = permission;
    }
}
