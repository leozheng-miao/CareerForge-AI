package com.leo.careerforgeai.interviewflow.api.session;

import com.leo.careerforgeai.interviewflow.application.session.InterviewSessionApplicationService;
import com.leo.careerforgeai.interviewflow.domain.session.*;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ComprehensiveInterviewControllerTest {
    @Test
    void createsComprehensiveInterviewWithConfirmedSettings() throws Exception {
        InterviewSessionApplicationService service = mock(InterviewSessionApplicationService.class);
        UUID id = UUID.fromString("74000000-0000-0000-0000-000000000001");
        when(service.createComprehensive(any())).thenReturn(new InterviewSession(id,
                new ActorId("owner"), InterviewMode.COMPREHENSIVE, SessionStage.PREPARING, 0));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new InterviewSessionController(service)).build();

        mvc.perform(post("/api/interviews/comprehensive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"74000000-0000-0000-0000-000000000002",
                                 "direction":"AI_AGENT_DEVELOPMENT","jobDescription":"Java Agent JD",
                                 "salaryRange":"30K-40K/月","durationSeconds":5400,
                                 "difficulty":"CHALLENGE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("COMPREHENSIVE"))
                .andExpect(jsonPath("$.data.stage").value("PREPARING"));
    }
}
