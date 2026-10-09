package com.leo.careerforgeai.interviewflow.application.query;

import com.leo.careerforgeai.interviewflow.application.port.*;
import com.leo.careerforgeai.interviewflow.domain.session.*;
import com.leo.careerforgeai.interviewflow.domain.timer.InterviewTimer;
import com.leo.careerforgeai.interviewflow.domain.turn.*;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import java.time.*; import java.util.*;
import static org.assertj.core.api.Assertions.*; import static org.mockito.Mockito.*;

class InterviewQueryServiceTest {
    @Test void returnsFullPresentedQuestionAndServerDraftForOwner() {
        ActorId owner=new ActorId("owner"); UUID id=UUID.randomUUID(), qid=UUID.randomUUID();
        var sessions=mock(InterviewSessionRepository.class); var questions=mock(InterviewQuestionRepository.class);
        var answers=mock(InterviewAnswerRepository.class); var reports=mock(InterviewReportRepository.class);
        InterviewAggregate aggregate=new InterviewAggregate(new InterviewSession(id,owner,InterviewMode.QUICK,SessionStage.INTERVIEWING,3),InterviewTimer.notStarted(Duration.ofHours(1)),0,0,0,Instant.EPOCH);
        InterviewQuestion question=new InterviewQuestion(qid,UUID.randomUUID(),InterviewQuestion.QuestionType.MAIN,10,"请完整说明你如何设计一个可恢复的 Agent 任务流程。",QuestionStatus.PRESENTED);
        InterviewAnswer answer=InterviewAnswer.draft(UUID.randomUUID(),qid).saveDraft("我会先持久化业务事实");
        when(sessions.findById(owner,id)).thenReturn(Optional.of(aggregate)); when(questions.findQuestions(owner,id)).thenReturn(List.of(question));
        when(answers.findByQuestion(owner,id,qid)).thenReturn(Optional.of(new InterviewAnswerRepository.AnswerRecord(answer,null,null,null,null,null,null,null,2)));
        var service=new InterviewQueryService(()->owner,sessions,questions,answers,reports);
        var result=service.currentQuestion(id);
        assertThat(result.question().content()).contains("Agent"); assertThat(result.draft()).isEqualTo("我会先持久化业务事实"); assertThat(result.answerVersion()).isEqualTo(2);
    }
    @Test void mapsCrossOwnerLookupToNotFound() {
        ActorId owner=new ActorId("owner"); UUID id=UUID.randomUUID(); var sessions=mock(InterviewSessionRepository.class);
        var service=new InterviewQueryService(()->owner,sessions,mock(InterviewQuestionRepository.class),mock(InterviewAnswerRepository.class),mock(InterviewReportRepository.class));
        assertThatThrownBy(()->service.get(id)).isInstanceOf(com.leo.careerforgeai.interviewflow.application.session.InterviewFlowNotFoundException.class);
    }
    @Test void mapsMissingReportToDedicatedNotFound() {
        ActorId owner=new ActorId("owner"); UUID id=UUID.randomUUID();
        var sessions=mock(InterviewSessionRepository.class); var reports=mock(InterviewReportRepository.class);
        InterviewAggregate aggregate=new InterviewAggregate(new InterviewSession(id,owner,InterviewMode.QUICK,SessionStage.COMPLETED,4),InterviewTimer.notStarted(Duration.ofHours(1)),0,0,0,Instant.EPOCH);
        when(sessions.findById(owner,id)).thenReturn(Optional.of(aggregate));
        when(reports.findByInterview(owner,id)).thenReturn(Optional.empty());
        var service=new InterviewQueryService(()->owner,sessions,mock(InterviewQuestionRepository.class),mock(InterviewAnswerRepository.class),reports);
        assertThatThrownBy(()->service.report(id)).isInstanceOf(com.leo.careerforgeai.interviewflow.application.report.InterviewReportNotFoundException.class);
    }
}
