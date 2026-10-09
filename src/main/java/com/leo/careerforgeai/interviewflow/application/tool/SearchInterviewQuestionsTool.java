package com.leo.careerforgeai.interviewflow.application.tool;

import com.leo.careerforgeai.agent.application.tool.AgentTool;
import com.leo.careerforgeai.agent.domain.tool.AgentToolOutput;
import com.leo.careerforgeai.agent.domain.tool.ToolContract;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionContext;
import com.leo.careerforgeai.agent.domain.tool.ToolImplementationType;
import com.leo.careerforgeai.agent.domain.tool.ToolRiskLevel;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort.KnowledgeQuestionCandidate;
import com.leo.careerforgeai.model.domain.toolcalling.ToolDefinition;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 面试Agent唯一只读工具，检索带来源公共题库候选
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@Component
public final class SearchInterviewQuestionsTool implements AgentTool<
        SearchInterviewQuestionsTool.Input,
        SearchInterviewQuestionsTool.Output> {

    public static final String NAME = "search_interview_questions";

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{"query":{"type":"string","minLength":1,"maxLength":500},"objective":{"type":"string","minLength":1,"maxLength":500},"limit":{"type":"integer","minimum":1,"maximum":5}},"required":["query","objective","limit"],"additionalProperties":false}
            """;
    private static final String OUTPUT_SCHEMA = """
            {"type":"object","properties":{"candidates":{"type":"array","maxItems":5}},"required":["candidates"],"additionalProperties":false}
            """;
    private static final ToolContract<Input, Output> CONTRACT =
            new ToolContract<>(
                    new ToolDefinition(
                            NAME,
                            "只读搜索管理员公共面经，返回带来源Chunk的候选题素材。",
                            INPUT_SCHEMA
                    ),
                    OUTPUT_SCHEMA,
                    Input.class,
                    Output.class,
                    ToolImplementationType.RETRIEVAL_BACKED,
                    ToolRiskLevel.LOW,
                    true,
                    1_500,
                    12_000,
                    5,
                    Duration.ofSeconds(10)
            );

    private final InterviewQuestionKnowledgePort knowledgePort;

    public SearchInterviewQuestionsTool(
            InterviewQuestionKnowledgePort knowledgePort
    ) {
        this.knowledgePort = Objects.requireNonNull(
                knowledgePort,
                "knowledgePort不能为空"
        );
    }

    @Override
    public ToolContract<Input, Output> contract() {
        return CONTRACT;
    }

    @Override
    public AgentToolOutput<Output> execute(
            Input input,
            ToolExecutionContext context
    ) {
        Objects.requireNonNull(input, "input不能为空");
        Objects.requireNonNull(context, "context不能为空");
        if (context.ownerId() == null || context.interviewId() == null) {
            throw new IllegalArgumentException(
                    "面试工具必须由服务端注入owner和session"
            );
        }
        List<KnowledgeQuestionCandidate> candidates = knowledgePort.search(
                input.query(),
                input.objective(),
                input.limit()
        );
        Output output = new Output(
                candidates == null ? List.of() : candidates
        );
        return AgentToolOutput.retrievalBacked(
                output,
                output.candidates().size(),
                null
        );
    }

    public record Input(
            @NotBlank @Size(max = 500) String query,
            @NotBlank @Size(max = 500) String objective,
            @Min(1) @Max(5) int limit
    ) {
    }

    public record Output(List<KnowledgeQuestionCandidate> candidates) {

        public Output {
            Objects.requireNonNull(candidates, "candidates不能为空");
            if (candidates.size() > 5
                    || candidates.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("工具候选数量不合法");
            }
            candidates = List.copyOf(candidates);
        }
    }
}
