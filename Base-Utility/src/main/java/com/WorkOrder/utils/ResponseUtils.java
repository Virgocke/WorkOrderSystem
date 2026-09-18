package com.WorkOrder.utils;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.Result;

import java.util.Arrays;

/**
 * 统一响应数据提取工具。
 */
public final class ResponseUtils {

    private ResponseUtils() {
    }

    /**
     * 校验响应并提取数据，保留业务错误码并避免空数据引起空指针异常。
     *
     * @param result 待校验的响应
     * @param missingDataError 响应成功但数据为空时抛出的业务错误
     * @param <T> 响应数据类型
     * @return 响应数据
     * @throws SystemException 响应为空、响应失败或数据为空时抛出
     */
    public static <T> T getResponseData(Result<T> result, SystemExceptionEnum missingDataError) {
        if (result == null) {
            throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
        if (result.getCode() != SystemExceptionEnum.SUCCESS.getCode()) {
            SystemExceptionEnum error = Arrays.stream(SystemExceptionEnum.values())
                    .filter(value -> value.getCode() == result.getCode())
                    .findFirst().orElse(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
            throw new SystemException(error);
        }
        if (result.getData() == null) {
            throw new SystemException(missingDataError);
        }
        return result.getData();
    }
}
