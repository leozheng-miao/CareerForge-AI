package com.leo.careerforgeai.interviewflow;

import com.leo.careerforgeai.interviewflow.domain.session.*;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.time.Instant;import java.time.temporal.ChronoUnit;import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class QuickInterviewFlowIT {
    @Test void completesEightTargetsWithFourBudgetedFollowUpsAndRequestsReport() {
        var machine=new InterviewStateMachine(new QuickInterviewPolicy());Instant now=Instant.parse("2026-10-06T00:00:00Z");
        InterviewAggregate value=InterviewAggregate.createQuick(UUID.randomUUID(),new ActorId("owner"),now,new QuickInterviewPolicy());
        value=machine.transition(value,InterviewEvent.PREPARATION_COMPLETED,now).aggregate();
        value=machine.transition(value,InterviewEvent.FORMAL_START_REQUESTED,now).aggregate();
        int answers=0;
        for(int target=1;target<=8;target++){
            int followUps=target<=2?2:0;
            for(int turn=0;turn<=followUps;turn++){
                now=now.plus(1,ChronoUnit.MINUTES);value=machine.transition(value,InterviewEvent.ANSWER_SUBMITTED,now).aggregate();answers++;
                if(turn<followUps)value=machine.transition(value,InterviewEvent.FOLLOW_UP_SELECTED,now).aggregate();
                else if(target<8)value=machine.transition(value,InterviewEvent.NEXT_TOPIC_SELECTED,now).aggregate();
                if(target<8||turn<followUps)value=machine.transition(value,InterviewEvent.RESUME_REQUESTED,now).aggregate();
            }
        }
        var finished=machine.transition(value,InterviewEvent.FINISH_REQUESTED,now);
        assertThat(answers).isEqualTo(12);assertThat(finished.aggregate().followUpCount()).isEqualTo(4);
        assertThat(finished.aggregate().submittedAnswerCount()).isEqualTo(12);
        assertThat(finished.aggregate().session().stage()).isEqualTo(SessionStage.GENERATING_REPORT);
        assertThat(finished.shouldGenerateReport()).isTrue();
    }
}
