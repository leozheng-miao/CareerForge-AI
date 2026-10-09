package com.leo.careerforgeai.interviewflow.api;

import com.leo.careerforgeai.interviewflow.api.answer.InterviewAnswerController;
import com.leo.careerforgeai.interviewflow.api.query.InterviewQueryController;
import com.leo.careerforgeai.interviewflow.api.report.InterviewReportController;
import com.leo.careerforgeai.interviewflow.api.session.InterviewSessionController;
import com.leo.careerforgeai.interviewflow.api.session.InterviewTimerController;
import com.leo.careerforgeai.interviewflow.api.sse.InterviewFlowEventController;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import java.lang.reflect.Method;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class InterviewFlowOpenApiContractTest {
    @Test void everyNewControllerOperationIsDocumentedAndUsesInterviewPath() {
        List<Class<?>> types=List.of(InterviewSessionController.class, InterviewTimerController.class,
                InterviewAnswerController.class, InterviewQueryController.class,
                InterviewReportController.class, InterviewFlowEventController.class);
        assertThat(types).allSatisfy(type -> {
            assertThat(type.getAnnotation(RequestMapping.class).value()[0]).startsWith("/api/interviews");
            assertThat(List.of(type.getDeclaredMethods()).stream()
                    .filter(this::isEndpoint).toList())
                    .isNotEmpty().allSatisfy(method -> assertThat(method.getAnnotation(Operation.class)).isNotNull());
        });
    }
    private boolean isEndpoint(Method method) {
        return List.of(method.getAnnotations()).stream().anyMatch(annotation ->
                annotation.annotationType().getPackageName().equals("org.springframework.web.bind.annotation")
                        && annotation.annotationType().getSimpleName().endsWith("Mapping"));
    }
}
