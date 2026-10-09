package com.leo.careerforgeai.interviewflow.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class LegacyInterviewApiExposureTest {
    @Test void legacyControllersRequireExplicitLegacyProperty() {
        List<Class<?>> controllers=List.of(
                com.leo.careerforgeai.interview.api.controller.MockInterviewController.class,
                com.leo.careerforgeai.interview.api.controller.InterviewAnswerController.class,
                com.leo.careerforgeai.interview.api.controller.InterviewQuestionController.class,
                com.leo.careerforgeai.interview.api.controller.InterviewReportController.class,
                com.leo.careerforgeai.interview.api.controller.InterviewEventController.class);
        assertThat(controllers).allSatisfy(type -> {
            ConditionalOnExpression condition=type.getAnnotation(ConditionalOnExpression.class);
            assertThat(condition).isNotNull();
            assertThat(condition.value()).contains("careerforge.interview.legacy-api-enabled:false");
        });
    }
}
