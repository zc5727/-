package com.hbjt.fitness.agent.web;

import com.hbjt.fitness.agent.dto.ApiError;
import com.hbjt.fitness.agent.support.AgentException;
import com.hbjt.fitness.agent.support.BadRequestException;
import com.hbjt.fitness.agent.support.NotFoundException;
import com.hbjt.fitness.agent.support.UpstreamException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * 全局异常处理：把各类异常收敛成统一 {@link ApiError} 结构。
 *
 * <p>解决的问题：原实现没有全局处理器，参数非法会以 500 返回、
 * 异常信息直接透给调用方，前端无法区分「你填错了」和「系统坏了」。
 * 这里明确区分：
 * <ul>
 *   <li>400 参数问题（含字段级错误明细），调用方可直接展示给用户</li>
 *   <li>404 会员不存在</li>
 *   <li>502 业务系统不可用</li>
 *   <li>500 其他未预期异常，仅回传通用文案，细节只进日志</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 字段校验失败：返回逐条明细，前端可精确标红 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e,
                                                     HttpServletRequest req) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .distinct()
                .toList();
        String message = details.isEmpty() ? "请求参数不合法" : String.join("；", details);
        return build(HttpStatus.BAD_REQUEST, "参数校验失败", message, req, details);
    }

    /** 请求体本身无法解析（如 JSON 语法错误、枚举值拼写错误） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e,
                                                     HttpServletRequest req) {
        String detail = e.getMostSpecificCause().getMessage();
        log.warn("请求体解析失败 path={} detail={}", req.getRequestURI(), detail);
        return build(HttpStatus.BAD_REQUEST, "请求格式错误",
                "请求体无法解析，请检查 JSON 格式与枚举取值（gender/goal/activityLevel）",
                req, List.of(String.valueOf(detail)));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException e, HttpServletRequest req) {
        log.warn("参数不合法 path={} msg={}", req.getRequestURI(), e.getMessage());
        return build(HttpStatus.BAD_REQUEST, "参数不合法", e.publicMessage(), req, null);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException e, HttpServletRequest req) {
        log.warn("资源不存在 path={} msg={}", req.getRequestURI(), e.getMessage());
        return build(HttpStatus.NOT_FOUND, "资源不存在", e.publicMessage(), req, null);
    }

    @ExceptionHandler(UpstreamException.class)
    public ResponseEntity<ApiError> handleUpstream(UpstreamException e, HttpServletRequest req) {
        log.error("上游依赖异常 path={} detail={}", req.getRequestURI(), e.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "上游服务异常", e.publicMessage(), req, null);
    }

    @ExceptionHandler(AgentException.class)
    public ResponseEntity<ApiError> handleAgent(AgentException e, HttpServletRequest req) {
        log.error("智能体异常 path={} detail={}", req.getRequestURI(), e.getMessage());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "智能体内部错误", "智能体处理失败，请稍后重试", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e, HttpServletRequest req) {
        log.error("未预期异常 path={}", req.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "服务内部错误", "服务内部错误，请稍后重试", req, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, String message,
                                           HttpServletRequest req, List<String> details) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), error, message, req.getRequestURI(), details));
    }
}
