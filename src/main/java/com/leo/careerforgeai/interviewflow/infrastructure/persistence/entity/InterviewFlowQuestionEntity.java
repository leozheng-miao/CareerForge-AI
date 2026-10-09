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
 * @description: 映射interview_flow_question主问题、追问和只读题源引用
 * @author: Miao Zheng
 * @date: 2026-10-02
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("interview_flow_question")
public class InterviewFlowQuestionEntity {

    @TableId(value = "question_id", type = IdType.INPUT)
    private String questionId;

    @TableField("session_id")
    private String interviewId;

    @TableField("topic_id")
    private String topicId;

    @TableField("owner_id")
    private String ownerId;

    @TableField("parent_question_id")
    private String parentQuestionId;

    @TableField("question_type")
    private String questionType;

    @TableField("display_order")
    private Integer displayOrder;

    @TableField("question_text")
    private String questionText;

    @TableField("source_refs_json")
    private String sourceRefsJson;

    @TableField("content_fingerprint")
    private String contentFingerprint;

    @TableField("question_status")
    private String questionStatus;

    @TableField("plan_version")
    private Long planVersion;

    @TableField("version")
    private Long version;

    @TableField("presented_at")
    private Instant presentedAt;

    @TableField("closed_at")
    private Instant closedAt;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
