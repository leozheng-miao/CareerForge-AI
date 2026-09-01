package com.leo.careerforgeai.interview.application.question;

import com.leo.careerforgeai.interview.application.model.question.InterviewQuestionDraft;
import com.leo.careerforgeai.interview.application.port.InterviewRoleModelGateway;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestionType;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 将通过角色契约校验的首题、下一题或追问候选转换为不可变问题事实
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
@Component
public class InterviewQuestionFactory {

    private final JsonMapper jsonMapper;

    public InterviewQuestionFactory(JsonMapper jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper不能为空");
    }

    public InterviewQuestion createFirstQuestion(
            UUID questionId,
            UUID interviewId,
            UUID roundId,
            ActorId ownerId,
            InterviewRoleModelGateway.Result<InterviewQuestionDraft> result,
            Instant createdAt
    ) {
        return createQuestion(
                questionId,
                interviewId,
                roundId,
                ownerId,
                null,
                false,
                result,
                createdAt
        );
    }

    public InterviewQuestion createQuestion(
            UUID questionId,
            UUID interviewId,
            UUID roundId,
            ActorId ownerId,
            UUID parentQuestionId,
            boolean followUp,
            InterviewRoleModelGateway.Result<InterviewQuestionDraft> result,
            Instant createdAt
    ) {
        Objects.requireNonNull(questionId, "questionId不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(roundId, "roundId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(result, "result不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");
        if (followUp != (parentQuestionId != null)) {
            throw new IllegalArgumentException("followUp与parentQuestionId不匹配");
        }

        InterviewQuestionDraft draft = result.output();
        List<String> targetSkills = List.copyOf(draft.targetSkills());
        List<String> evaluationPoints = List.copyOf(draft.evaluationPoints());
        List<String> evidenceReferenceIds = List.copyOf(draft.evidenceReferenceIds());
        String contentHash = contentHash(
                draft.questionType(),
                draft.question(),
                draft.difficulty(),
                targetSkills,
                evaluationPoints,
                draft.followUpAllowed(),
                followUp,
                evidenceReferenceIds
        );

        return new InterviewQuestion(
                questionId,
                interviewId,
                roundId,
                ownerId,
                parentQuestionId,
                draft.questionType(),
                draft.question(),
                draft.difficulty(),
                targetSkills,
                evaluationPoints,
                draft.followUpAllowed(),
                followUp,
                evidenceReferenceIds,
                result.requestId(),
                result.promptVersion(),
                contentHash,
                createdAt
        );
    }

    private String contentHash(
            InterviewQuestionType questionType,
            String question,
            int difficulty,
            List<String> targetSkills,
            List<String> evaluationPoints,
            boolean followUpAllowed,
            boolean followUp,
            List<String> evidenceReferenceIds
    ) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("schemaVersion", 1);
        content.put("questionType", questionType);
        content.put("question", question);
        content.put("difficulty", difficulty);
        content.put("targetSkills", targetSkills);
        content.put("evaluationPoints", evaluationPoints);
        content.put("followUpAllowed", followUpAllowed);
        content.put("followUp", followUp);
        content.put("evidenceReferenceIds", evidenceReferenceIds);

        try {
            return sha256(jsonMapper.writeValueAsString(content));
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化问题内容失败", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }
}