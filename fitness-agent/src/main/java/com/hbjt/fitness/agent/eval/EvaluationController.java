package com.hbjt.fitness.agent.eval;

import com.hbjt.fitness.agent.support.BadRequestException;
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
 * 评测接口：跑批、查批次、查逐题结果、维护用例。
 *
 * <p>{@code withLlm=false}（默认）只跑确定性环节，秒级返回，适合频繁回归；
 * 设为 true 会连大模型一起跑，慢且结果有随机性，只在评估端到端效果时用。
 */
@RestController
@RequestMapping("/api/eval")
public class EvaluationController {

    private final EvaluationService eval;

    public EvaluationController(EvaluationService eval) {
        this.eval = eval;
    }

    /** 跑一轮评测，返回逐题结果。 */
    @PostMapping("/run")
    public Map<String, Object> run(
            @RequestParam(value = "withLlm", defaultValue = "false") boolean withLlm,
            @RequestParam(value = "limit", defaultValue = "200") int limit) {
        return eval.run(withLlm, limit);
    }

    /** 历史批次。 */
    @GetMapping("/runs")
    public Map<String, Object> runs(@RequestParam(value = "limit", defaultValue = "20") int limit) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("items", eval.runs(limit));
        return res;
    }

    /** 某批次的逐题结果。 */
    @GetMapping("/runs/{id}/results")
    public Map<String, Object> results(@PathVariable("id") long id) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("runId", id);
        res.put("items", eval.results(id));
        return res;
    }

    /** 新增评测用例。字段：category、question、expectIntent、expectKeywords。 */
    @PostMapping("/cases")
    public Map<String, Object> addCase(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> res = new LinkedHashMap<>();
        Map<String, Object> b = body == null ? Map.of() : body;
        String question = b.get("question") == null ? null : String.valueOf(b.get("question"));
        if (question == null || question.isBlank()) {
            throw new BadRequestException("评测用例必须提供 question", "question 为空");
        }
        int n = eval.addCase(
                b.get("category") == null ? null : String.valueOf(b.get("category")),
                question,
                b.get("expectIntent") == null ? null : String.valueOf(b.get("expectIntent")),
                b.get("expectKeywords") == null ? null : String.valueOf(b.get("expectKeywords")));
        res.put("ok", n > 0);
        res.put("added", n);
        return res;
    }
}
