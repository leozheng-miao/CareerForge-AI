package com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 映射interview_flow_answer草稿、冻结回答和最终决策事实
 * @author: Miao Zheng
 * @date: 2026-10-02
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("interview_flow_answer")
public class InterviewFlowAnswerEntity {

    @TableId(value = "answer_id", type = IdType.INPUT)
    private String answerId;

    @TableField("session_id")
    private String interviewId;

    @TableField("question_id")
    private String questionId;

    @TableField("owner_id")
    private String ownerId;

    @TableField("draft_text")
    private String draftText;

    @TableField("draft_version")
    private Long draftVersion;

    @TableField("frozen_answer")
    private String frozenAnswer;

    @TableField("answer_content_hash")
    private String answerContentHash;

    @TableField("submit_request_id")
    private String submitRequestId;

    @TableField("submit_request_fingerprint")
    private String submitRequestFingerprint;

    @TableField("answer_status")
    private String answerStatus;

    @TableField("turn_analysis_json")
    private String turnAnalysisJson;

    @TableField("model_decision_json")
    private String modelDecisionJson;

    @TableField("final_decision")
    private String finalDecision;

    @TableField("submitted_at")
    private Instant submittedAt;

    @TableField("version")
    private Long version;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
