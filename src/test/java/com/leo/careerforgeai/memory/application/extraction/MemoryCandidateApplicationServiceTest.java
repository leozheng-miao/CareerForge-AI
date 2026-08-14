package com.leo.careerforgeai.memory.application.extraction;

import com.leo.careerforgeai.memory.application.extraction.dto.ExtractedMemoryCandidate;
import com.leo.careerforgeai.memory.application.extraction.dto.MemoryExtractionResult;
import com.leo.careerforgeai.memory.application.extraction.dto.MemoryExtractionTurnInput;
import com.leo.careerforgeai.memory.application.port.conversation.CoachingConversationRepository;
import com.leo.careerforgeai.memory.application.port.extraction.MemoryExtractionReceiptRepository;
import com.leo.careerforgeai.memory.application.port.profile.MemoryRepository;
import com.leo.careerforgeai.memory.domain.conversation.CoachingSession;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.memory.domain.extraction.MemoryExtractionInputIdentity;
import com.leo.careerforgeai.memory.domain.extraction.MemoryExtractionReceipt;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.profile.MemorySource;
import com.leo.careerforgeai.memory.domain.profile.MemorySourceType;
import com.leo.careerforgeai.memory.domain.profile.MemoryStatus;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import com.leo.careerforgeai.memory.domain.profile.TimeConstraintKey;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证Memory提取的owner边界、成功Receipt回放、空结果幂等和候选原子写入
 * @author: Miao Zheng
 * @date: 2026-08-14
 **/
class MemoryCandidateApplicationServiceTest {

    private static final ActorId ACTOR_A = new ActorId("actor-a");
    private static final UUID SESSION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TURN_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-13T02:00:00Z");

    private final CurrentActorProvider actorProvider =
            mock(CurrentActorProvider.class);
    private final CoachingConversationRepository conversationRepository =
            mock(CoachingConversationRepository.class);
    private final MemoryRepository memoryRepository =
            mock(MemoryRepository.class);
    private final MemoryExtractionReceiptRepository receiptRepository =
            mock(MemoryExtractionReceiptRepository.class);
    private final MemoryExtractionFingerprintGenerator fingerprintGenerator =
            new MemoryExtractionFingerprintGenerator();
    private final MemoryCandidateExtractor extractor =
            mock(MemoryCandidateExtractor.class);
    private final PendingMemoryCandidateWriter writer =
            mock(PendingMemoryCandidateWriter.class);

    private MemoryCandidateApplicationService service;

    @BeforeEach
    void setUp() {
        service = new MemoryCandidateApplicationService(
                actorProvider,
                conversationRepository,
                memoryRepository,
                receiptRepository,
                fingerprintGenerator,
                extractor,
                writer
        );
    }

    @Test
    void shouldExtractAndPersistWhenReceiptDoesNotExist() {
        ConversationTurn turn = userTurn();
        MemoryExtractionInputIdentity identity =
                fingerprintGenerator.generate(List.of(turn));
        MemoryExtractionResult extractionResult = extractionResult();
        MemoryItem candidate = pendingCandidate();
        MemoryCandidateApplicationResult freshResult =
                applicationResult(List.of(candidate), false);

        stubOwnedTurn(turn);
        when(receiptRepository.findByIdentity(
                ACTOR_A,
                identity.extractorVersion(),
                identity.inputFingerprint()
        )).thenReturn(Optional.empty());
        when(extractor.extract(any())).thenReturn(extractionResult);
        when(writer.save(
                ACTOR_A,
                List.of(turn),
                identity,
                extractionResult
        )).thenReturn(freshResult);

        MemoryCandidateApplicationResult result =
                service.extract(SESSION_ID, List.of(TURN_ID));

        assertThat(result).isEqualTo(freshResult);
        assertThat(result.replayed()).isFalse();

        verify(extractor).extract(List.of(
                new MemoryExtractionTurnInput(
                        TURN_ID,
                        turn.role(),
                        turn.content()
                )
        ));
        verify(writer).save(
                ACTOR_A,
                List.of(turn),
                identity,
                extractionResult
        );
    }

    @Test
    void shouldReplayExistingReceiptWithoutCallingModel() {
        ConversationTurn turn = userTurn();
        MemoryItem candidate = pendingCandidate();
        MemoryExtractionReceipt receipt =
                receipt(List.of(candidate.memoryId()));

        stubOwnedTurn(turn);
        when(receiptRepository.findByIdentity(
                ACTOR_A,
                receipt.inputIdentity().extractorVersion(),
                receipt.inputIdentity().inputFingerprint()
        )).thenReturn(Optional.of(receipt));
        when(memoryRepository.findById(
                ACTOR_A,
                candidate.memoryId()
        )).thenReturn(Optional.of(candidate));

        MemoryCandidateApplicationResult result =
                service.extract(SESSION_ID, List.of(TURN_ID));

        assertThat(result.candidates()).containsExactly(candidate);
        assertThat(result.replayed()).isTrue();
        assertThat(result.modelRequestId())
                .isEqualTo(receipt.modelRequestId());

        verifyNoInteractions(extractor, writer);
    }

    @Test
    void shouldReplayEmptyReceiptWithoutCallingModel() {
        ConversationTurn turn = userTurn();
        MemoryExtractionReceipt receipt = receipt(List.of());

        stubOwnedTurn(turn);
        when(receiptRepository.findByIdentity(
                ACTOR_A,
                receipt.inputIdentity().extractorVersion(),
                receipt.inputIdentity().inputFingerprint()
        )).thenReturn(Optional.of(receipt));

        MemoryCandidateApplicationResult result =
                service.extract(SESSION_ID, List.of(TURN_ID));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.replayed()).isTrue();

        verifyNoInteractions(memoryRepository, extractor, writer);
    }

    @Test
    void shouldRejectForeignTurnBeforeReceiptLookupOrModelCall() {
        when(actorProvider.currentActor()).thenReturn(ACTOR_A);
        when(conversationRepository.findSession(ACTOR_A, SESSION_ID))
                .thenReturn(Optional.of(session()));
        when(conversationRepository.findTurn(ACTOR_A, TURN_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.extract(SESSION_ID, List.of(TURN_ID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Turn不存在或不属于当前用户");

        verifyNoInteractions(
                memoryRepository,
                receiptRepository,
                extractor,
                writer
        );
    }

    @Test
    void shouldCreatePendingCandidateAndReceiptInWriter() {
        MemoryRepository repository = mock(MemoryRepository.class);
        MemoryExtractionReceiptRepository receipts =
                mock(MemoryExtractionReceiptRepository.class);
        PendingMemoryCandidateWriter realWriter = writer(
                repository,
                receipts
        );

        when(repository.findByOwnerAndNormalizedKey(
                ACTOR_A,
                MemoryType.TIME_CONSTRAINT,
                MemoryNormalizedKey.timeConstraint(
                        TimeConstraintKey.WEEKLY_HOURS
                )
        )).thenReturn(List.of());

        MemoryCandidateApplicationResult result = realWriter.save(
                ACTOR_A,
                List.of(userTurn()),
                inputIdentity(userTurn()),
                extractionResult()
        );

        MemoryItem candidate = result.candidates().getFirst();
        assertThat(candidate.status()).isEqualTo(MemoryStatus.PENDING);
        assertThat(candidate.ownerId()).isEqualTo(ACTOR_A);
        assertThat(candidate.source().sourceId())
                .isEqualTo(TURN_ID.toString());
        assertThat(candidate.extractionModelRequestId())
                .isEqualTo("memory-request-1");
        assertThat(result.replayed()).isFalse();

        verify(repository).insert(candidate);

        ArgumentCaptor<MemoryExtractionReceipt> receiptCaptor =
                ArgumentCaptor.forClass(MemoryExtractionReceipt.class);
        verify(receipts).insert(receiptCaptor.capture());

        assertThat(receiptCaptor.getValue().memoryIds())
                .containsExactly(candidate.memoryId());
    }

    @Test
    void shouldPersistSuccessfulEmptyReceipt() {
        MemoryRepository repository = mock(MemoryRepository.class);
        MemoryExtractionReceiptRepository receipts =
                mock(MemoryExtractionReceiptRepository.class);
        PendingMemoryCandidateWriter realWriter = writer(
                repository,
                receipts
        );

        MemoryExtractionResult emptyResult = new MemoryExtractionResult(
                List.of(),
                "memory-request-empty",
                new ModelUsage(80, 10, 90),
                20,
                1
        );

        MemoryCandidateApplicationResult result = realWriter.save(
                ACTOR_A,
                List.of(userTurn()),
                inputIdentity(userTurn()),
                emptyResult
        );

        assertThat(result.candidates()).isEmpty();

        ArgumentCaptor<MemoryExtractionReceipt> receiptCaptor =
                ArgumentCaptor.forClass(MemoryExtractionReceipt.class);
        verify(receipts).insert(receiptCaptor.capture());

        assertThat(receiptCaptor.getValue().memoryIds()).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void shouldReusePendingCandidateWhenSameSourceIsParaphrased() {
        MemoryRepository repository = mock(MemoryRepository.class);
        MemoryExtractionReceiptRepository receipts =
                mock(MemoryExtractionReceiptRepository.class);
        PendingMemoryCandidateWriter realWriter = writer(
                repository,
                receipts
        );
        MemoryItem existing = pendingCandidate(
                "未来半年，每周最多投入10小时学习Java和AI应用开发"
        );

        when(repository.findByOwnerAndNormalizedKey(
                ACTOR_A,
                existing.type(),
                existing.normalizedKey()
        )).thenReturn(List.of(existing));

        MemoryCandidateApplicationResult result = realWriter.save(
                ACTOR_A,
                List.of(userTurn()),
                inputIdentity(userTurn()),
                extractionResult(
                        "未来半年每周最多可投入10小时学习Java和AI应用开发",
                        TURN_ID
                )
        );

        assertThat(result.candidates()).containsExactly(existing);
        verify(repository, never()).insert(any());
        verify(receipts).insert(any(MemoryExtractionReceipt.class));
    }

    @Test
    void shouldKeepSameSlotCandidatesFromDifferentSourcesSeparate() {
        MemoryRepository repository = mock(MemoryRepository.class);
        MemoryExtractionReceiptRepository receipts =
                mock(MemoryExtractionReceiptRepository.class);
        PendingMemoryCandidateWriter realWriter = writer(
                repository,
                receipts
        );
        MemoryItem existing = pendingCandidate();
        ConversationTurn otherTurn = otherUserTurn();

        when(repository.findByOwnerAndNormalizedKey(
                ACTOR_A,
                existing.type(),
                existing.normalizedKey()
        )).thenReturn(List.of(existing));

        MemoryCandidateApplicationResult result = realWriter.save(
                ACTOR_A,
                List.of(otherTurn),
                inputIdentity(otherTurn),
                extractionResult(
                        "我每周可以学习8小时",
                        otherTurn.turnId()
                )
        );

        assertThat(result.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.memoryId())
                    .isNotEqualTo(existing.memoryId());
            assertThat(candidate.source().sourceId())
                    .isEqualTo(otherTurn.turnId().toString());
        });

        verify(repository).insert(result.candidates().getFirst());
    }

    private void stubOwnedTurn(ConversationTurn turn) {
        when(actorProvider.currentActor()).thenReturn(ACTOR_A);
        when(conversationRepository.findSession(ACTOR_A, SESSION_ID))
                .thenReturn(Optional.of(session()));
        when(conversationRepository.findTurn(ACTOR_A, turn.turnId()))
                .thenReturn(Optional.of(turn));
    }

    private CoachingSession session() {
        return CoachingSession.create(
                SESSION_ID,
                ACTOR_A,
                "Memory提取",
                NOW
        );
    }

    private PendingMemoryCandidateWriter writer(
            MemoryRepository repository,
            MemoryExtractionReceiptRepository receipts
    ) {
        return new PendingMemoryCandidateWriter(
                repository,
                receipts,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private MemoryExtractionInputIdentity inputIdentity(
            ConversationTurn turn
    ) {
        return fingerprintGenerator.generate(List.of(turn));
    }

    private MemoryExtractionReceipt receipt(List<UUID> memoryIds) {
        return new MemoryExtractionReceipt(
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                ACTOR_A,
                inputIdentity(userTurn()),
                memoryIds,
                "memory-request-1",
                new ModelUsage(100, 30, 130),
                25,
                1,
                NOW
        );
    }

    private MemoryCandidateApplicationResult applicationResult(
            List<MemoryItem> candidates,
            boolean replayed
    ) {
        return new MemoryCandidateApplicationResult(
                candidates,
                "memory-request-1",
                new ModelUsage(100, 30, 130),
                25,
                1,
                replayed
        );
    }

    private ConversationTurn userTurn() {
        return ConversationTurn.completedUser(
                TURN_ID,
                SESSION_ID,
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                ACTOR_A,
                1,
                "我每周可以学习10小时",
                NOW.minusSeconds(60)
        );
    }

    private ConversationTurn otherUserTurn() {
        return ConversationTurn.completedUser(
                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                SESSION_ID,
                UUID.fromString("30000000-0000-0000-0000-000000000002"),
                ACTOR_A,
                2,
                "我每周可以学习8小时",
                NOW.minusSeconds(30)
        );
    }

    private MemoryExtractionResult extractionResult() {
        return extractionResult("我每周可以学习10小时", TURN_ID);
    }

    private MemoryExtractionResult extractionResult(
            String content,
            UUID sourceTurnId
    ) {
        ExtractedMemoryCandidate candidate = new ExtractedMemoryCandidate(
                MemoryType.TIME_CONSTRAINT,
                MemoryNormalizedKey.timeConstraint(
                        TimeConstraintKey.WEEKLY_HOURS
                ),
                content,
                sourceTurnId,
                List.of(sourceTurnId),
                new BigDecimal("0.90")
        );

        return new MemoryExtractionResult(
                List.of(candidate),
                "memory-request-1",
                new ModelUsage(100, 30, 130),
                25,
                1
        );
    }

    private MemoryItem pendingCandidate() {
        return pendingCandidate("我每周可以学习10小时");
    }

    private MemoryItem pendingCandidate(String content) {
        return MemoryItem.createExtractedPending(
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                ACTOR_A,
                MemoryType.TIME_CONSTRAINT,
                MemoryNormalizedKey.timeConstraint(
                        TimeConstraintKey.WEEKLY_HOURS
                ),
                content,
                new MemorySource(
                        MemorySourceType.CONVERSATION_TURN,
                        TURN_ID.toString(),
                        userTurn().contentHash()
                ),
                "memory-request-1",
                new BigDecimal("0.90"),
                null,
                List.of(TURN_ID.toString()),
                NOW
        );
    }
}