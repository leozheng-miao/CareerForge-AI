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
 * @description: 映射interview_flow_topic八个考查目标的执行和总结事实
 * @author: Miao Zheng
 * @date: 2026-10-02
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("interview_flow_topic")
public class InterviewFlowTopicEntity {

    @TableId(value = "topic_id", type = IdType.INPUT)
    private String topicId;

    @TableField("session_id")
    private String interviewId;

    @TableField("owner_id")
    private String ownerId;

    @TableField("sequence_no")
    private Integer sequenceNo;

    @TableField("topic_type")
    private String topicType;

    @TableField("objective")
    private String objective;

    @TableField("topic_status")
    private String topicStatus;

    @TableField("coverage_result_json")
    private String coverageResultJson;

    @TableField("summary_json")
    private String summaryJson;

    @TableField("summary_version")
    private Long summaryVersion;

    @TableField("plan_version")
    private Long planVersion;

    @TableField("version")
    private Long version;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
