package com.leo.careerforgeai.memory.application.extraction;

import com.leo.careerforgeai.memory.application.extraction.dto.MemoryExtractionResult;
import com.leo.careerforgeai.memory.application.extraction.dto.MemoryExtractionTurnInput;
import com.leo.careerforgeai.memory.application.port.conversation.CoachingConversationRepository;
import com.leo.careerforgeai.memory.application.port.extraction.MemoryExtractionReceiptRepository;
import com.leo.careerforgeai.memory.application.port.profile.MemoryRepository;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.memory.domain.extraction.MemoryExtractionInputIdentity;
import com.leo.careerforgeai.memory.domain.extraction.MemoryExtractionReceipt;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 编排当前用户Turn校验、成功Receipt回放、模型提取和PENDING Memory短事务写入
 * @author: Miao Zheng
 * @date: 2026-08-14
 **/
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MemoryCandidateApplicationService {

    public static final int MAX_SELECTED_TURNS = 20;

    private final CurrentActorProvider currentActorProvider;
    private final CoachingConversationRepository conversationRepository;
    private final MemoryRepository memoryRepository;
    private final MemoryExtractionReceiptRepository receiptRepository;
    private final MemoryExtractionFingerprintGenerator fingerprintGenerator;
    private final MemoryCandidateExtractor extractor;
    private final PendingMemoryCandidateWriter candidateWriter;

    public MemoryCandidateApplicationService(
            CurrentActorProvider currentActorProvider,
            CoachingConversationRepository conversationRepository,
            MemoryRepository memoryRepository,
            MemoryExtractionReceiptRepository receiptRepository,
            MemoryExtractionFingerprintGenerator fingerprintGenerator,
            MemoryCandidateExtractor extractor,
            PendingMemoryCandidateWriter candidateWriter
    ) {
        this.currentActorProvider = Objects.requireNonNull(
                currentActorProvider,
                "currentActorProvider不能为空"
        );
        this.conversationRepository = Objects.requireNonNull(
                conversationRepository,
                "conversationRepository不能为空"
        );
        this.memoryRepository = Objects.requireNonNull(
                memoryRepository,
                "memoryRepository不能为空"
        );
        this.receiptRepository = Objects.requireNonNull(
                receiptRepository,
                "receiptRepository不能为空"
        );
        this.fingerprintGenerator = Objects.requireNonNull(
                fingerprintGenerator,
                "fingerprintGenerator不能为空"
        );
        this.extractor = Objects.requireNonNull(extractor, "extractor不能为空");
        this.candidateWriter = Objects.requireNonNull(
                candidateWriter,
                "candidateWriter不能为空"
        );
    }

    /**
     * 从当前用户显式选择的Turn中提取或回放候选。
     * 模型调用不能被数据库事务包围。
     */
    public MemoryCandidateApplicationResult extract(
            UUID sessionId,
            List<UUID> turnIds
    ) {
        Objects.requireNonNull(sessionId, "sessionId不能为空");
        List<UUID> validatedTurnIds = validateTurnIds(turnIds);
        ActorId actorId = currentActorProvider.currentActor();

        conversationRepository.findSession(actorId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Session不存在或不属于当前用户"
                ));

        List<ConversationTurn> sourceTurns = validatedTurnIds.stream()
                .map(turnId -> requireOwnedCompletedTurn(
                        actorId,
                        sessionId,
                        turnId
                ))
                .sorted(Comparator.comparingLong(
                        ConversationTurn::turnSequence
                ))
                .toList();

        MemoryExtractionInputIdentity inputIdentity =
                fingerprintGenerator.generate(sourceTurns);

        return receiptRepository.findByIdentity(
                        actorId,
                        inputIdentity.extractorVersion(),
                        inputIdentity.inputFingerprint()
                )
                .map(receipt -> replay(actorId, receipt))
                .orElseGet(() -> executeExtraction(
                        actorId,
                        sourceTurns,
                        inputIdentity
                ));
    }

    private MemoryCandidateApplicationResult executeExtraction(
            ActorId actorId,
            List<ConversationTurn> sourceTurns,
            MemoryExtractionInputIdentity inputIdentity
    ) {
        List<MemoryExtractionTurnInput> modelInputs = sourceTurns.stream()
                .map(turn -> new MemoryExtractionTurnInput(
                        turn.turnId(),
                        turn.role(),
                        turn.content()
                ))
                .toList();

        MemoryExtractionResult extractionResult =
                extractor.extract(modelInputs);

        return candidateWriter.save(
                actorId,
                sourceTurns,
                inputIdentity,
                extractionResult
        );
    }

    private MemoryCandidateApplicationResult replay(
            ActorId actorId,
            MemoryExtractionReceipt receipt
    ) {
        if (!actorId.equals(receipt.ownerId())) {
            throw new IllegalStateException("Memory提取凭证owner不一致");
        }

        List<MemoryItem> candidates = receipt.memoryIds().stream()
                .map(memoryId -> memoryRepository.findById(actorId, memoryId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Memory提取凭证关联的Memory不存在"
                        )))
                .toList();

        return new MemoryCandidateApplicationResult(
                candidates,
                receipt.modelRequestId(),
                receipt.modelUsage(),
                receipt.modelDurationMs(),
                receipt.modelCallCount(),
                true
        );
    }

    private ConversationTurn requireOwnedCompletedTurn(
            ActorId actorId,
            UUID sessionId,
            UUID turnId
    ) {
        ConversationTurn turn = conversationRepository.findTurn(actorId, turnId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Turn不存在或不属于当前用户"
                ));

        if (!turn.sessionId().equals(sessionId)) {
            throw new IllegalArgumentException("Turn不属于当前Session");
        }
        if (!turn.isEligibleForMemoryExtraction()) {
            throw new IllegalArgumentException(
                    "只有COMPLETED Turn可以参与Memory提取"
            );
        }
        return turn;
    }

    private List<UUID> validateTurnIds(List<UUID> turnIds) {
        if (turnIds == null || turnIds.isEmpty()
                || turnIds.size() > MAX_SELECTED_TURNS) {
            throw new IllegalArgumentException("turnIds数量不合法");
        }
        if (turnIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("turnIds不能包含空值");
        }

        LinkedHashSet<UUID> distinctIds = new LinkedHashSet<>(turnIds);
        if (distinctIds.size() != turnIds.size()) {
            throw new IllegalArgumentException("turnIds不能重复");
        }
        return List.copyOf(distinctIds);
    }
}