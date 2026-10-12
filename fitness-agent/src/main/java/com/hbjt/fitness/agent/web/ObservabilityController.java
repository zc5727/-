package com.hbjt.fitness.agent.web;

import com.hbjt.fitness.agent.observability.TraceRecorder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运行观测接口。
 *
 * <p>只提供查询能力，写入完全由 {@link TraceRecorder} 在调用链路上自动完成——
 * 不允许外部直接往 trace 表写数据，否则指标就可以被随意粉饰了。
 *
 * <p>三个接口的定位：
 * <ul>
 *   <li>{@code /overview}：聚合指标，给仪表盘用（调用量、失败率、延迟分位、意图分布）</li>
 *   <li>{@code /traces}：最近流水，含各阶段耗时，给瀑布图用</li>
 *   <li>{@code /daily/refresh}：手工触发按天聚合（仪表盘数据量变大后的加速手段）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {

    private final TraceRecorder recorder;

    public ObservabilityController(TraceRecorder recorder) {
        this.recorder = recorder;
    }

    /**
     * 运行概览。
     *
     * @param days 统计窗口天数，1-90，默认 7
     */
    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam(value = "days", defaultValue = "7") int days) {
        return recorder.overview(days);
    }

    /**
     * 最近的调用流水。
     *
     * @param limit  返回条数，1-200，默认 30
     * @param intent 按意图过滤，可空
     * @param status 按状态过滤（OK / ASK / DEGRADED / ERROR），可空
     */
    @GetMapping("/traces")
    public Map<String, Object> traces(
            @RequestParam(value = "limit", defaultValue = "30") int limit,
            @RequestParam(value = "intent", required = false) String intent,
            @RequestParam(value = "status", required = false) String status) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("available", recorder.isAvailable());
        res.put("items", recorder.traces(limit, intent, status));
        return res;
    }

    /** 手工刷新按天聚合指标。 */
    @PostMapping("/daily/refresh")
    public Map<String, Object> refreshDaily(@RequestParam(value = "days", defaultValue = "30") int days) {
        return recorder.refreshDaily(days);
    }
}
