package com.leo.careerforgeai.interview.application.model.question;

import com.leo.careerforgeai.interview.domain.session.InterviewMode;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义面试官角色生成单个问题所需的冻结输入
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param interviewId 面试UUID
 * @param roundNo 当前回合号
 * @param mode 面试模式
 * @param questionType 本轮问题类型
 * @param difficulty 难度等级，范围1至5
 * @param blueprintSummary Java生成的冻结面试蓝图摘要
 * @param targetRoleSummary 已确认目标岗位摘要
 * @param evidenceByChunkId 本轮允许模型读取和引用的冻结证据片段ID及正文
 * @param completedQuestionSummaries 已完成问题摘要，用于避免重复
 * @param currentRoundGoal 本轮确定性考察目标
 **/
public record InterviewQuestionInput(
        @NotNull UUID interviewId,
        @Min(1) int roundNo,
        @NotNull InterviewMode mode,
        @NotNull InterviewQuestionType questionType,
        @Min(1) @Max(5) int difficulty,
        @NotBlank @Size(max = 8_000) String blueprintSummary,
        @NotBlank @Size(max = 8_000) String targetRoleSummary,
        @NotNull @Size(max = 20) Map<@Pattern(regexp = "[0-9a-f]{64}") String, @NotBlank @Size(max = 2_000) String> evidenceByChunkId,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 1_000) String> completedQuestionSummaries,
        @NotBlank @Size(max = 1_000) String currentRoundGoal
) {
}