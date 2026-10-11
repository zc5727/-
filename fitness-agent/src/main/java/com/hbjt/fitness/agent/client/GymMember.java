package com.hbjt.fitness.agent.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 业务系统 member 表的读出视图（只映射智能体用得到的字段）。
 * <p>注意：该表没有体脂率字段，因此 bodyFat 恒为 null，
 * 由指标层按 Deurenberg 公式估算并标注来源。
 * <p>忽略未知字段，业务系统加字段不会导致智能体解析失败。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GymMember(
        @JsonProperty("memberNo") Long memberNo,
        @JsonProperty("memberName") String name,
        @JsonProperty("memberGender") String genderCode,
        @JsonProperty("memberAge") Integer age,
        @JsonProperty("memberHeight") Double height,
        @JsonProperty("memberWeight") Double weight,
        @JsonProperty("memberPhone") String phone,
        @JsonProperty("cardClass") Integer cardClass
) {
}
