package com.leo.careerforgeai.interviewflow.application.report;

import com.leo.careerforgeai.interviewflow.application.report.InterviewEvidencePack.TurnEvidence;
import com.leo.careerforgeai.model.application.ModelGateway;
import com.leo.careerforgeai.model.domain.ModelMessage;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelRole;
import com.leo.careerforgeai.model.domain.routing.ModelTaskType;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 生成并严格校验无分数、可回链原文的正式报告。 */
@Component
public final class InterviewReportEngine {
    private static final int MAX_CITATION_LENGTH = 240;
    private static final String SYSTEM_PROMPT = """
            你是 CareerForge 快速面试报告组件。只能根据输入的冻结回答和回合分析写报告。
            不得打分、不得给百分比、不得使用“部分面试”或“部分报告”。
            跳过或未考察主题不得写成弱项。后续回答已补充的问题不得继续写成缺口。
            输入仅包含允许评价的已考察主题，topicReviews.sequenceNo只能使用输入中存在的sequenceNo。
            每条优势或改进项必须从输入 citations 中选择一个citationId；不得自己编写、改写或复制quote。
            只输出JSON：
            {"summary":"...","strengths":[{"title":"...","description":"...","citationId":"..."}],
             "improvements":[{"title":"...","description":"...","citationId":"..."}],
             "topicReviews":[{"sequenceNo":1,"conclusion":"...","answerIds":["UUID"]}]}
            """;

    private final ModelGateway modelGateway;
    private final JsonMapper jsonMapper;

    public InterviewReportEngine(ModelGateway modelGateway, JsonMapper jsonMapper) {
        this.modelGateway = Objects.requireNonNull(modelGateway);
        this.jsonMapper = Objects.requireNonNull(jsonMapper);
    }

    public GeneratedReport generate(InterviewEvidencePack evidence) {
        Objects.requireNonNull(evidence, "evidence不能为null");
        if (evidence.submittedAnswerCount() == 0) {
            throw new IllegalStateException("没有已提交回答时不得生成报告");
        }
        try {
            ModelEvidence modelEvidence = modelEvidence(evidence);
            String input = jsonMapper.writeValueAsString(modelEvidence);
            String output = modelGateway.chat(
                    ModelTaskType.INTERVIEW_REPORT,
                    new ModelRequest(
                            List.of(new ModelMessage(ModelRole.SYSTEM, SYSTEM_PROMPT),
                                    new ModelMessage(ModelRole.USER, input)),
                            ModelOutputFormat.JSON_OBJECT,
                            2_500,
                            0.0,
                            Duration.ofSeconds(60)
                    )
            ).content();
            ModelGeneratedReport modelReport = jsonMapper.readerFor(ModelGeneratedReport.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(output);
            GeneratedReport report = resolveCitations(modelReport, modelEvidence);
            GeneratedReport safeReport = removeUncoveredTopicReviews(report, evidence);
            validate(safeReport, evidence);
            return safeReport;
        } catch (JacksonException exception) {
            throw new IllegalStateException("面试报告不是合法结构", exception);
        }
    }

    private static ModelEvidence modelEvidence(InterviewEvidencePack evidence) {
        return new ModelEvidence(
                evidence.interviewId(),
                evidence.topics().stream()
                        .filter(topic -> topic.covered() && !topic.turns().isEmpty())
                        .map(topic -> new ModelTopicEvidence(
                                topic.sequenceNo(),
                                topic.objective(),
                                topic.turns().stream()
                                        .map(turn -> new ModelTurnEvidence(
                                                turn.answerId(),
                                                turn.questionId(),
                                                turn.question(),
                                                turn.analysisJson(),
                                                citations(turn)
                                        ))
                                        .toList()
                        ))
                        .toList()
        );
    }

    private static List<Citation> citations(TurnEvidence turn) {
        String answer = turn.frozenAnswer();
        java.util.ArrayList<Citation> citations = new java.util.ArrayList<>();
        int start = 0;
        int sequence = 1;
        while (start < answer.length()) {
            int end = Math.min(start + MAX_CITATION_LENGTH, answer.length());
            if (end < answer.length()) {
                int boundary = preferredBoundary(answer, start, end);
                if (boundary > start) {
                    end = boundary;
                } else if (Character.isHighSurrogate(answer.charAt(end - 1))
                        && Character.isLowSurrogate(answer.charAt(end))) {
                    end--;
                }
            }
            String quote = answer.substring(start, end).strip();
            if (!quote.isEmpty()) {
                citations.add(new Citation(
                        turn.answerId() + ":" + sequence++,
                        turn.answerId(),
                        quote
                ));
            }
            start = end;
        }
        return List.copyOf(citations);
    }

    private static int preferredBoundary(String answer, int start, int end) {
        int earliest = start + (MAX_CITATION_LENGTH / 2);
        for (int index = end - 1; index >= earliest; index--) {
            char current = answer.charAt(index);
            if (current == '。' || current == '！' || current == '？'
                    || current == '!' || current == '?' || current == '；'
                    || current == ';' || current == '\n') {
                return index + 1;
            }
        }
        return -1;
    }

    private static GeneratedReport resolveCitations(
            ModelGeneratedReport report,
            ModelEvidence evidence
    ) {
        Map<String, Citation> allowedCitations = evidence.topics().stream()
                .flatMap(topic -> topic.turns().stream())
                .flatMap(turn -> turn.citations().stream())
                .collect(Collectors.toUnmodifiableMap(
                        Citation::citationId,
                        Function.identity()
                ));
        return new GeneratedReport(
                report.summary(),
                resolveFindings(report.strengths(), allowedCitations),
                resolveFindings(report.improvements(), allowedCitations),
                report.topicReviews()
        );
    }

    private static List<Finding> resolveFindings(
            List<ModelFinding> findings,
            Map<String, Citation> allowedCitations
    ) {
        return findings.stream().map(finding -> {
            Citation citation = allowedCitations.get(finding.citationId());
            if (citation == null) {
                throw new IllegalStateException("报告引用了未知原文片段");
            }
            return new Finding(
                    finding.title(),
                    finding.description(),
                    citation.answerId(),
                    citation.quote()
            );
        }).toList();
    }

    private static GeneratedReport removeUncoveredTopicReviews(
            GeneratedReport report,
            InterviewEvidencePack evidence
    ) {
        Set<Integer> coveredSequences = evidence.topics().stream()
                .filter(topic -> topic.covered() && !topic.turns().isEmpty())
                .map(InterviewEvidencePack.TopicEvidence::sequenceNo)
                .collect(Collectors.toUnmodifiableSet());
        List<TopicReview> safeReviews = report.topicReviews().stream()
                .filter(review -> coveredSequences.contains(review.sequenceNo()))
                .toList();
        return new GeneratedReport(
                report.summary(),
                report.strengths(),
                report.improvements(),
                safeReviews
        );
    }

    private static void validate(GeneratedReport report, InterviewEvidencePack evidence) {
        Objects.requireNonNull(report, "report不能为null");
        String serializedText = report.summary() + report.strengths()
                + report.improvements() + report.topicReviews();
        if (serializedText.matches(
                "(?s).*(?:\\d+(?:\\.\\d+)?\\s*(?:%|分)"
                        + "|\\d+\\s*/\\s*\\d+).*"
        )) {
            throw new IllegalStateException("正式面试报告不得包含数值评分");
        }
        if (serializedText.contains("部分面试") || serializedText.contains("部分报告")) {
            throw new IllegalStateException("报告不得使用部分标签");
        }
        List<TurnEvidence> turns = evidence.topics().stream()
                .flatMap(topic -> topic.turns().stream()).toList();
        report.allFindings().forEach(finding -> {
            TurnEvidence turn = turns.stream()
                    .filter(item -> item.answerId().equals(finding.answerId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("报告引用了未知答案"));
            if (finding.quote() == null || finding.quote().isBlank()
                    || !turn.frozenAnswer().contains(finding.quote())) {
                throw new IllegalStateException("报告引文无法回链到冻结回答");
            }
        });
        report.topicReviews().forEach(review -> {
            boolean covered = evidence.topics().stream()
                    .anyMatch(topic -> topic.sequenceNo() == review.sequenceNo()
                            && topic.covered());
            if (!covered) {
                throw new IllegalStateException("报告不得评价未考察主题");
            }
        });
    }

    public record GeneratedReport(String summary, List<Finding> strengths,
                                  List<Finding> improvements,
                                  List<TopicReview> topicReviews) {
        public GeneratedReport {
            if (summary == null || summary.isBlank()) {
                throw new IllegalArgumentException("summary不能为空");
            }
            strengths = strengths == null ? List.of() : List.copyOf(strengths);
            improvements = improvements == null ? List.of() : List.copyOf(improvements);
            topicReviews = topicReviews == null ? List.of() : List.copyOf(topicReviews);
        }
        List<Finding> allFindings() {
            return java.util.stream.Stream.concat(strengths.stream(), improvements.stream()).toList();
        }
    }

    public record Finding(String title, String description,
                          java.util.UUID answerId, String quote) {
        public Finding {
            if (title == null || title.isBlank() || description == null || description.isBlank()) {
                throw new IllegalArgumentException("报告结论不完整");
            }
            Objects.requireNonNull(answerId, "answerId不能为null");
        }
    }

    public record TopicReview(int sequenceNo, String conclusion,
                              List<java.util.UUID> answerIds) {
        public TopicReview {
            if (sequenceNo < 1 || conclusion == null || conclusion.isBlank()) {
                throw new IllegalArgumentException("主题结论不完整");
            }
            answerIds = answerIds == null ? List.of() : List.copyOf(answerIds);
        }
    }

    private record ModelEvidence(UUID interviewId, List<ModelTopicEvidence> topics) {
    }

    private record ModelTopicEvidence(
            int sequenceNo,
            String objective,
            List<ModelTurnEvidence> turns
    ) {
    }

    private record ModelTurnEvidence(
            UUID answerId,
            UUID questionId,
            String question,
            String analysisJson,
            List<Citation> citations
    ) {
    }

    private record Citation(String citationId, UUID answerId, String quote) {
    }

    private record ModelGeneratedReport(
            String summary,
            List<ModelFinding> strengths,
            List<ModelFinding> improvements,
            List<TopicReview> topicReviews
    ) {
        private ModelGeneratedReport {
            strengths = strengths == null ? List.of() : List.copyOf(strengths);
            improvements = improvements == null ? List.of() : List.copyOf(improvements);
            topicReviews = topicReviews == null ? List.of() : List.copyOf(topicReviews);
        }
    }

    private record ModelFinding(String title, String description, String citationId) {
        private ModelFinding {
            if (title == null || title.isBlank()
                    || description == null || description.isBlank()
                    || citationId == null || citationId.isBlank()) {
                throw new IllegalArgumentException("报告结论引用不完整");
            }
        }
    }
}
