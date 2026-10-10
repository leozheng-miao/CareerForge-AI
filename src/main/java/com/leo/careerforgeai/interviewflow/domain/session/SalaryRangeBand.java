package com.leo.careerforgeai.interviewflow.domain.session;

import java.util.Arrays;

/** 综合面试首版支持的税前月薪区间。 */
public enum SalaryRangeBand {
    BELOW_10K("10K/月以下"),
    FROM_10K_TO_15K("10K-15K/月"),
    FROM_15K_TO_20K("15K-20K/月"),
    FROM_20K_TO_30K("20K-30K/月"),
    FROM_30K_TO_40K("30K-40K/月"),
    FROM_40K_TO_60K("40K-60K/月"),
    FROM_60K_TO_80K("60K-80K/月"),
    ABOVE_80K("80K/月以上");

    private final String value;

    SalaryRangeBand(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static String requireSupported(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("salaryRange不能为空");
        }
        String normalized = value.strip();
        return Arrays.stream(values())
                .map(SalaryRangeBand::value)
                .filter(normalized::equals)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("salaryRange不在允许的薪资区间内"));
    }
}
