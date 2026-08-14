package com.leo.careerforgeai.memory.api;

import com.leo.careerforgeai.memory.application.profile.MemoryProfileQueryApplicationService;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecision;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecisionType;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.profile.MemorySource;
import com.leo.careerforgeai.memory.domain.profile.MemorySourceType;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import com.leo.careerforgeai.memory.domain.profile.TimeConstraintKey;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @program: CareerForge-AI
 * @description: 验证PENDING候选和CONFIRMED画像API的返回字段及内部数据脱敏
 * @author: Miao Zheng
 * @date: 2026-08-14
 **/
@ExtendWith(MockitoExtension.class)
class MemoryProfileControllerTest {

    private static final UUID MEMORY_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID TURN_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-14T04:00:00Z");

    @Mock
    private MemoryProfileQueryApplicationService queryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MemoryProfileController(queryService))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(
                        JsonMapper.builder().build()
                ))
                .build();
    }

    @Test
    void shouldReturnPendingCandidatesWithoutInternalOwnershipOrHashes() throws Exception {
        when(queryService.findPendingCandidates()).thenReturn(List.of(pendingMemory()));

        mockMvc.perform(get("/api/memories/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].memoryId").value(MEMORY_ID.toString()))
                .andExpect(jsonPath("$.data[0].type").value("TIME_CONSTRAINT"))
                .andExpect(jsonPath("$.data[0].normalizedKey").value("weekly_hours"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].sourceId").value(TURN_ID.toString()))
                .andExpect(jsonPath("$.data[0].version").value(0))
                .andExpect(content().string(not(containsString("ownerId"))))
                .andExpect(content().string(not(containsString("contentHash"))))
                .andExpect(content().string(not(containsString("sourceHash"))))
                .andExpect(content().string(not(containsString("extractionModelRequestId"))))
                .andExpect(content().string(not(containsString("sourceAgentRunId"))));

        verify(queryService).findPendingCandidates();
    }

    @Test
    void shouldReturnConfirmedProfile() throws Exception {
        when(queryService.findConfirmedProfile())
                .thenReturn(List.of(confirmedMemory()));

        mockMvc.perform(get("/api/memories/confirmed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].memoryId").value(MEMORY_ID.toString()))
                .andExpect(jsonPath("$.data[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data[0].version").value(1))
                .andExpect(jsonPath("$.data[0].sourceId").value(TURN_ID.toString()))
                .andExpect(content().string(not(containsString("ownerId"))))
                .andExpect(content().string(not(containsString("sourceHash"))))
                .andExpect(content().string(not(containsString("extractionModelRequestId"))));

        verify(queryService).findConfirmedProfile();
    }

    private MemoryItem confirmedMemory() {
        MemoryItem candidate = pendingMemory();
        MemoryDecision decision = MemoryDecision.create(
                UUID.randomUUID(),
                candidate,
                candidate.ownerId(),
                MemoryDecisionType.CONFIRM,
                null,
                "用户确认",
                NOW.plusSeconds(1)
        );
        return candidate.applyDecision(decision);
    }

    private MemoryItem pendingMemory() {
        return MemoryItem.createExtractedPending(
                MEMORY_ID,
                new ActorId("actor-a"),
                MemoryType.TIME_CONSTRAINT,
                MemoryNormalizedKey.timeConstraint(TimeConstraintKey.WEEKLY_HOURS),
                "我每周可以学习10小时",
                new MemorySource(
                        MemorySourceType.CONVERSATION_TURN,
                        TURN_ID.toString(),
                        "a".repeat(64)
                ),
                "model-request-1",
                new BigDecimal("0.90"),
                null,
                List.of(TURN_ID.toString()),
                NOW
        );
    }
}