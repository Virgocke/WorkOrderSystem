package com.WorkOrder.user.dto;

import com.WorkOrder.user.model.Users;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Virgor
 * @date 2026年09月12日 02:44
 * @description 用户DTO
 */
@Data
public class UsersDto extends Users {
    private Set<String> permissions = new LinkedHashSet<>();
}
