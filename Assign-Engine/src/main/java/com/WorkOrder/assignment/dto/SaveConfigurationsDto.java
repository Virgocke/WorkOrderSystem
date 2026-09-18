package com.WorkOrder.assignment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/** 管理员批量保存系统配置，版本号取自配置查询接口。 */
@Data
public class SaveConfigurationsDto {
    @Valid
    @NotEmpty(message = "没有需要保存的配置")
    @Size(max = 5, message = "一次最多保存5项配置")
    private List<@NotNull(message = "配置项不能为空") Item> items;

    @Data
    public static class Item {
        @NotBlank(message = "配置键不能为空")
        private String configKey;
        @NotNull(message = "配置值不能为空")
        private JsonNode value;
        @NotNull(message = "配置版本不能为空")
        @Min(value = 0, message = "配置版本不能小于0")
        private Long version;
    }
}
