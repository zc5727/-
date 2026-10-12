package com.gym.controller;

import com.gym.entity.EquipmentMaintenance;
import com.gym.service.OpsIntelligenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运营中枢接口。
 *
 * <p>分成两组：
 * <ul>
 *   <li>{@code /ops/...}：会员经营——分层重算、概览、分层名单</li>
 *   <li>{@code /ops/equipment/...}：器材工单——建单、派工、完成、验收、流转日志</li>
 * </ul>
 *
 * <p>所有写操作都设计成可重复执行：建单走 INSERT，状态推进走
 * 「带前置状态的 UPDATE」，重复提交影响行数为 0，不产生副作用。
 */
@RestController
@RequestMapping("/ops")
public class OpsController {

    @Autowired
    private OpsIntelligenceService ops;

    /* ==================== 会员经营 ==================== */

    /**
     * 重算会员分层与流失信号，并生成当日快照。
     *
     * @param days RFM 统计窗口，1-365，默认 90
     */
    @PostMapping("/refresh")
    public Map<String, Object> refresh(@RequestParam(value = "days", defaultValue = "90") int days) {
        Map<String, Object> res = new LinkedHashMap<>();
        int span = Math.max(1, Math.min(days, 365));
        Map<String, Object> r = ops.refresh(span);
        res.put("days", span);
        res.putAll(r);
        return res;
    }

    /** 运营概览：分层分布、流失风险榜、近期快照趋势。 */
    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam(value = "days", defaultValue = "30") int days) {
        int span = Math.max(1, Math.min(days, 90));
        Map<String, Object> r = ops.overview(span);
        r.put("days", span);
        return r;
    }

    /** 分层名单；segment 为空返回全部。 */
    @GetMapping("/segments")
    public Map<String, Object> segments(@RequestParam(value = "segment", required = false) String segment) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("items", ops.listSegments(segment));
        return res;
    }

    /* ==================== 器材维护工单 ==================== */

    /** 建单。字段：equipmentNo（必填）、planType、title、plannedDate、operator */
    @PostMapping("/equipment/orders")
    public Map<String, Object> createOrder(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? new LinkedHashMap<>() : body;
        Integer no = b.get("equipmentNo") == null ? null : Integer.valueOf(String.valueOf(b.get("equipmentNo")));
        if (no == null) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("ok", false);
            res.put("reason", "缺少 equipmentNo");
            return res;
        }
        return ops.createOrder(no,
                str(b.get("planType")), str(b.get("title")),
                str(b.get("plannedDate")), str(b.get("operator")));
    }

    /** 推进工单状态。字段：to（ASSIGNED/DONE/VERIFIED）、assignee、cost、operator */
    @PostMapping("/equipment/orders/{id}/advance")
    public Map<String, Object> advance(@PathVariable("id") long id,
                                       @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? new LinkedHashMap<>() : body;
        Double cost = null;
        if (b.get("cost") != null) {
            try {
                cost = Double.valueOf(String.valueOf(b.get("cost")));
            } catch (NumberFormatException ignored) {
                cost = null;
            }
        }
        return ops.advanceOrder(id, str(b.get("to")), str(b.get("assignee")), cost,
                str(b.get("operator")));
    }

    /** 工单列表；status 为空返回全部。 */
    @GetMapping("/equipment/orders")
    public Map<String, Object> orders(@RequestParam(value = "status", required = false) String status) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("items", ops.listOrders(status));
        return res;
    }

    /** 工单流转日志。 */
    @GetMapping("/equipment/orders/{id}/logs")
    public Map<String, Object> logs(@PathVariable("id") long id) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("orderId", id);
        res.put("items", ops.orderLogs(id));
        return res;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
