package com.hbjt.fitness.agent.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 统一错误契约。所有异常出口都返回这个结构，前端只需处理一种形状。
 */
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details
) {
    public static ApiError of(int status, String error, String message, String path, List<String> details) {
        return new ApiError(OffsetDateTime.now(), status, error, message, path, details);
    }
}
