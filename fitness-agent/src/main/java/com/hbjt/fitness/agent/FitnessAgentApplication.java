package com.hbjt.fitness.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 健身会员智慧管理系统 —— 智能问答智能体（独立服务）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>不碰业务数据：会员档案通过 REST 从业务系统读取，不直连业务库；
 *       只拥有自己那一份会话数据（独立 schema），见 db 包</li>
 *   <li>证据驱动：先算可复算的生理指标，再让 LLM 组织语言，数字不交给模型编</li>
 *   <li>可降级：LLM 不可用时空结果一律判为降级，规则引擎兜底，永不返回空；
 *       知识库与数据库不可用时同样只降级、不阻塞启动</li>
 *   <li>知识库热更新：knowledge 目录以卷挂载，定时比对目录签名后重建内存索引</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FitnessAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitnessAgentApplication.class, args);
    }
}
