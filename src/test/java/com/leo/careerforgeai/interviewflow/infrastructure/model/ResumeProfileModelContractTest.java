package com.leo.careerforgeai.interviewflow.infrastructure.model;

import com.leo.careerforgeai.interviewflow.application.resume.ProfileDraft;
import com.leo.careerforgeai.interviewflow.application.resume.ProfileDraftValidator;
import com.leo.careerforgeai.interviewflow.application.resume.ProfileExperienceDraft;
import com.leo.careerforgeai.model.application.ModelGateway;
import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.domain.routing.ModelTaskType;
import com.leo.careerforgeai.model.domain.stream.ModelStreamEvent;
import com.leo.careerforgeai.model.exception.completion.ModelCompletionException;
import com.leo.careerforgeai.model.exception.completion.ModelCompletionStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

class ResumeProfileModelContractTest {

    @Test
    void retriesOnceWithCompactContractWhenProviderReachesOutputLimit() {
        ModelCompletionException truncated = completionFailure(
                ModelCompletionStatus.OUTPUT_TOKEN_LIMIT_REACHED, "length");
        ScriptedGateway gateway = new ScriptedGateway(truncated, successfulResponse());

        ProfileDraft result = new ResumeProfileModelContract(
                gateway, JsonMapper.builder().build()).extract("四页中英文简历");

        assertThat(result.personalSummary()).isEqualTo("Java后端开发者");
        assertThat(gateway.tasks).containsExactly(
                ModelTaskType.RESUME_PROFILE_EXTRACTION,
                ModelTaskType.RESUME_PROFILE_EXTRACTION);
        assertThat(gateway.requests)
                .allSatisfy(request -> assertThat(request.maxOutputTokens()).isEqualTo(4_000));
        assertThat(gateway.requests.get(1).messages().getFirst().content())
                .contains("总经历最多12条", "每条描述最多240字符");
    }

    @Test
    void doesNotRetryProviderIncompleteReasonsOtherThanOutputLimit() {
        ModelCompletionException filtered = completionFailure(
                ModelCompletionStatus.CONTENT_FILTERED, "content_filter");
        ScriptedGateway gateway = new ScriptedGateway(filtered, successfulResponse());

        assertThatThrownBy(() -> new ResumeProfileModelContract(
                gateway, JsonMapper.builder().build()).extract("简历内容"))
                .isSameAs(filtered);
        assertThat(gateway.requests).hasSize(1);
    }

    @Test
    void rejectsProfilesThatExceedTheBoundedExperienceContract() {
        List<ProfileExperienceDraft> experiences = IntStream.range(0, 13)
                .mapToObj(index -> new ProfileExperienceDraft(
                        "经历" + index, "后端开发", "2024", "职责描述", List.of("Java")))
                .toList();
        ProfileDraft profile = new ProfileDraft(
                "Java后端开发者", List.of("Java"), experiences.subList(0, 4),
                experiences.subList(4, 8), experiences.subList(8, 13));

        assertThatThrownBy(() -> new ProfileDraftValidator().validate(profile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("经历总数");
    }

    @Test
    void compactsOverlongMultilineModelTextBeforeReturningTheProfile() {
        String overlongDescription = "负责 Java 后端开发\n\t"
                + "并完成可靠性优化。".repeat(40);
        ProfileDraft proposed = new ProfileDraft(
                "  Java Agent 后端开发者\n具备工程化经验  ",
                List.of(" Java\n", " Redis\t"),
                List.of(new ProfileExperienceDraft(
                        " CareerForge-AI ", " 后端开发\n", " 2026 ",
                        overlongDescription, List.of(" Spring Boot\n", " Redis\t"))),
                List.of(), List.of());

        ProfileDraft result = new ProfileDraftValidator().validate(proposed);

        assertThat(result.personalSummary()).isEqualTo("Java Agent 后端开发者 具备工程化经验");
        assertThat(result.coreSkills()).containsExactly("Java", "Redis");
        ProfileExperienceDraft experience = result.projectExperiences().getFirst();
        assertThat(experience.name()).isEqualTo("CareerForge-AI");
        assertThat(experience.description())
                .hasSize(240)
                .doesNotContain("\n", "\r", "\t");
        assertThat(experience.technologies()).containsExactly("Spring Boot", "Redis");
    }

    private static ModelCompletionException completionFailure(
            ModelCompletionStatus status, String finishReason) {
        return new ModelCompletionException(status, finishReason,
                "provider-request", "deepseek-flash",
                new ModelUsage(3_900, 1_800, 5_700), 100,
                "{\"personalSummary\":\"Java后端开发者\"");
    }

    private static ModelResponse successfulResponse() {
        return new ModelResponse("request-2", "deepseek-flash", """
                {"personalSummary":"Java后端开发者","coreSkills":["Java"],
                 "projectExperiences":[],"workExperiences":[],"internshipExperiences":[]}
                """, new ModelUsage(3_900, 120, 4_020));
    }

    private static final class ScriptedGateway implements ModelGateway {
        private final RuntimeException firstFailure;
        private final ModelResponse success;
        private final List<ModelTaskType> tasks = new ArrayList<>();
        private final List<ModelRequest> requests = new ArrayList<>();

        private ScriptedGateway(RuntimeException firstFailure, ModelResponse success) {
            this.firstFailure = firstFailure;
            this.success = success;
        }

        @Override
        public ModelResponse chat(ModelTaskType taskType, ModelRequest request) {
            tasks.add(taskType);
            requests.add(request);
            if (requests.size() == 1) throw firstFailure;
            return success;
        }

        @Override
        public void stream(ModelTaskType taskType, ModelRequest request,
                           Consumer<ModelStreamEvent> eventConsumer) {
            throw new UnsupportedOperationException();
        }
    }
}
