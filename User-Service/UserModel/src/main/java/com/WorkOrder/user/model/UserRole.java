package com.WorkOrder.user.model;

/**
 * 系统内置角色。细粒度授权由后续 RBAC 权限表承载。
 */
public enum UserRole {
    USER,
    HANDLER,
    ADMIN
}
