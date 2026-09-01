package com.leo.careerforgeai.interview.api.dto.question;

import com.leo.careerforgeai.interview.application.question.CurrentInterviewQuestionQueryApplicationService;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestionType;
import com.leo.careerforgeai.interview.domain.round.InterviewRound;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;

import java.time.Instant;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回当前待回答问题且不暴露评价要点、证据引用和模型内部字段
 * @author: Miao Zheng
 * @date: 2026-08-30
 * @param interviewId 模拟面试UUID
 * @param interviewVersion 提交答案时必须携带的面试版本
 * @param roundNo 当前回合号
 * @param questionId 当前问题UUID
 * @param parentQuestionId 追问对应的父问题UUID
 * @param questionType 问题类型
 * @param questionText 用户可见问题正文
 * @param difficulty 1至5级难度
 * @param followUp 当前问题是否为追问
 * @param followUpAllowed 当前问题是否允许继续追问
 * @param createdAt 问题创建时间
 **/
public record CurrentInterviewQuestionResponse(
        UUID interviewId,
        long interviewVersion,
        int roundNo,
        UUID questionId,
        UUID parentQuestionId,
        InterviewQuestionType questionType,
        String questionText,
        int difficulty,
        boolean followUp,
        boolean followUpAllowed,
        Instant createdAt
) {

    public static CurrentInterviewQuestionResponse from(
            CurrentInterviewQuestionQueryApplicationService.CurrentQuestion current
    ) {
        MockInterviewSession session = current.session();
        InterviewRound round = current.round();
        InterviewQuestion question = current.question();

        return new CurrentInterviewQuestionResponse(
                session.interviewId(),
                session.version(),
                round.roundNo(),
                question.questionId(),
                question.parentQuestionId(),
                question.questionType(),
                question.questionText(),
                question.difficulty(),
                question.followUp(),
                question.followUpAllowed(),
                question.createdAt()
        );
    }
}