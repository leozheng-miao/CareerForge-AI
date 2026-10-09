package com.leo.careerforgeai.interviewflow.application.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewContextAssemblerTest {

    @Test
    void shouldSegmentLongCurrentAnswerWithoutDroppingTail() {
        String answer = "第一段。" + "a".repeat(2_100)
                + "\n第二段保留未解决矛盾。";

        var segments = InterviewContextAssembler.semanticSegments(answer);

        assertThat(segments).hasSizeGreaterThan(1);
        assertThat(String.join("", segments).replace("\n", ""))
                .contains("第一段。")
                .contains("第二段保留未解决矛盾。");
        assertThat(segments).allMatch(segment -> segment.length() <= 2_000);
    }
}
