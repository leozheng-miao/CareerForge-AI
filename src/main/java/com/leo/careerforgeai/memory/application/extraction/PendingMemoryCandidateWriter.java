package com.leo.careerforgeai.memory.application.extraction;

import com.leo.careerforgeai.memory.application.extraction.dto.ExtractedMemoryCandidate;
import com.leo.careerforgeai.memory.application.extraction.dto.MemoryExtractionErrorType;
import com.leo.careerforgeai.memory.application.extraction.dto.MemoryExtractionException;
import com.leo.careerforgeai.memory.application.extraction.dto.MemoryExtractionFailureStage;
import com.leo.careerforgeai.memory.application.extraction.dto.MemoryExtractionResult;
import com.leo.careerforgeai.memory.application.port.extraction.MemoryExtractionReceiptRepository;
import com.leo.careerforgeai.memory.application.port.profile.MemoryRepository;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.memory.domain.extraction.MemoryExtractionInputIdentity;
import com.leo.careerforgeai.memory.domain.extraction.MemoryExtractionReceipt;
import com.leo.careerforgeai.memory.domain.extraction.MemoryExtractionSourceSnapshot;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemorySource;
import com.leo.careerforgeai.memory.domain.profile.MemorySourceType;
import com.leo.careerforgeai.memory.domain.profile.MemoryStatus;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 在同一短事务中保存PENDING候选、复用重复结果并写入成功提取凭证
 * @author: Miao Zheng
 * @date: 2026-08-14
 **/
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class PendingMemoryCandidateWriter {

    private final MemoryRepository memoryRepository;
    private final MemoryExtractionReceiptRepository receiptRepository;
    private final Clock clock;

    public PendingMemoryCandidateWriter(
            MemoryRepository memoryRepository,
            MemoryExtractionReceiptRepository receiptRepository,
            Clock clock
    ) {
        this.memoryRepository = Objects.requireNonNull(
                memoryRepository,
                "memoryRepository不能为空"
        );
        this.receiptRepository = Objects.requireNonNull(
                receiptRepository,
                "receiptRepository不能为空"
        );
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    /**
     * 候选、空结果和成功Receipt必须在同一事务内提交。
     * 模型调用已经在事务外完成。
     */
    @Transactional
    public MemoryCandidateApplicationResult save(
            ActorId actorId,
            List<ConversationTurn> sourceTurns,
            MemoryExtractionInputIdentity inputIdentity,
            MemoryExtractionResult extractionResult
    ) {
        Objects.requireNonNull(actorId, "actorId不能为空");
        Objects.requireNonNull(inputIdentity, "inputIdentity不能为空");
        Objects.requireNonNull(extractionResult, "extractionResult不能为空");

        Map<UUID, ConversationTurn> sourceTurnMap =
                indexSourceTurns(actorId, sourceTurns);

        validateInputIdentity(sourceTurnMap, inputIdentity);
        rejectConflictingSingleValueCandidates(extractionResult);

        List<MemoryItem> candidates = extractionResult.candidates().stream()
                .map(candidate -> saveOrReuse(
                        actorId,
                        candidate,
                        sourceTurnMap,
                        extractionResult
                ))
                .toList();

        MemoryExtractionReceipt receipt = new MemoryExtractionReceipt(
                UUID.randomUUID(),
                actorId,
                inputIdentity,
                candidates.stream().map(MemoryItem::memoryId).toList(),
                extractionResult.modelRequestId(),
                extractionResult.modelUsage(),
                extractionResult.modelDurationMs(),
                extractionResult.modelCallCount(),
                clock.instant()
        );

        receiptRepository.insert(receipt);

        return new MemoryCandidateApplicationResult(
                candidates,
                extractionResult.modelRequestId(),
                extractionResult.modelUsage(),
                extractionResult.modelDurationMs(),
                extractionResult.modelCallCount(),
                false
        );
    }

    private MemoryItem saveOrReuse(
            ActorId actorId,
            ExtractedMemoryCandidate extracted,
            Map<UUID, ConversationTurn> sourceTurnMap,
            MemoryExtractionResult extractionResult
    ) {
        ConversationTurn sourceTurn =
                sourceTurnMap.get(extracted.sourceTurnId());

        if (sourceTurn == null) {
            throw invalidOutput(
                    "Memory候选主要来源不在服务端Turn集合",
                    extractionResult
            );
        }

        MemorySource source = new MemorySource(
                MemorySourceType.CONVERSATION_TURN,
                sourceTurn.turnId().toString(),
                sourceTurn.contentHash()
        );

        MemoryItem candidate = MemoryItem.createExtractedPending(
                UUID.randomUUID(),
                actorId,
                extracted.type(),
                extracted.normalizedKey(),
                extracted.content(),
                source,
                extractionResult.modelRequestId(),
                extracted.confidence(),
                sourceTurn.agentRunId(),
                extracted.evidenceTurnIds().stream()
                        .map(UUID::toString)
                        .toList(),
                clock.instant()
        );

        List<MemoryItem> existingCandidates =
                memoryRepository.findByOwnerAndNormalizedKey(
                        actorId,
                        candidate.type(),
                        candidate.normalizedKey()
                );

        return findReusableCandidate(existingCandidates, candidate)
                .orElseGet(() -> {
                    memoryRepository.insert(candidate);
                    return candidate;
                });
    }

    private Optional<MemoryItem> findReusableCandidate(
            List<MemoryItem> existingCandidates,
            MemoryItem candidate
    ) {
        Optional<MemoryItem> exactDuplicate =
                existingCandidates.stream()
                        .filter(existing -> hasSameSource(
                                existing,
                                candidate
                        ))
                        .filter(existing -> existing.contentHash()
                                .equals(candidate.contentHash()))
                        .findFirst();

        if (exactDuplicate.isPresent()) {
            return exactDuplicate;
        }

        return existingCandidates.stream()
                .filter(existing ->
                        existing.status() == MemoryStatus.PENDING)
                .filter(existing -> hasSameSource(existing, candidate))
                .findFirst();
    }

    private boolean hasSameSource(
            MemoryItem existing,
            MemoryItem candidate
    ) {
        return existing.source().sourceType()
                == candidate.source().sourceType()
                && existing.source().sourceId()
                .equals(candidate.source().sourceId());
    }

    private Map<UUID, ConversationTurn> indexSourceTurns(
            ActorId actorId,
            List<ConversationTurn> sourceTurns
    ) {
        if (sourceTurns == null || sourceTurns.isEmpty()) {
            throw new IllegalArgumentException("sourceTurns不能为空");
        }

        Map<UUID, ConversationTurn> result = new LinkedHashMap<>();
        for (ConversationTurn turn : sourceTurns) {
            if (turn == null || !actorId.equals(turn.ownerId())) {
                throw new IllegalArgumentException("来源Turn归属不合法");
            }
            if (!turn.isEligibleForMemoryExtraction()) {
                throw new IllegalArgumentException(
                        "来源Turn必须是COMPLETED状态"
                );
            }
            if (result.putIfAbsent(turn.turnId(), turn) != null) {
                throw new IllegalArgumentException("来源Turn不能重复");
            }
        }
        return Map.copyOf(result);
    }

    private void validateInputIdentity(
            Map<UUID, ConversationTurn> sourceTurnMap,
            MemoryExtractionInputIdentity inputIdentity
    ) {
        if (sourceTurnMap.size() != inputIdentity.sources().size()) {
            throw new IllegalArgumentException("提取身份与来源Turn数量不一致");
        }

        for (MemoryExtractionSourceSnapshot snapshot
                : inputIdentity.sources()) {
            ConversationTurn turn = sourceTurnMap.get(snapshot.turnId());

            if (turn == null
                    || !turn.sessionId().equals(snapshot.sessionId())
                    || turn.turnSequence() != snapshot.turnSequence()
                    || !turn.contentHash().equals(snapshot.sourceHash())) {
                throw new IllegalArgumentException(
                        "提取身份与来源Turn快照不一致"
                );
            }
        }
    }

    private void rejectConflictingSingleValueCandidates(
            MemoryExtractionResult extractionResult
    ) {
        Map<String, String> singleValueContents = new HashMap<>();

        for (ExtractedMemoryCandidate candidate
                : extractionResult.candidates()) {
            if (candidate.type() == MemoryType.SKILL_EVIDENCE) {
                continue;
            }

            String slot = candidate.type()
                    + ":"
                    + candidate.normalizedKey().value();

            String previousContent = singleValueContents.putIfAbsent(
                    slot,
                    candidate.content().strip()
            );

            if (previousContent != null
                    && !previousContent.equals(candidate.content().strip())) {
                throw invalidOutput(
                        "同一提取结果包含冲突的单值Memory候选",
                        extractionResult
                );
            }
        }
    }

    private MemoryExtractionException invalidOutput(
            String safeMessage,
            MemoryExtractionResult extractionResult
    ) {
        return new MemoryExtractionException(
                MemoryExtractionErrorType.MODEL_OUTPUT_INVALID,
                MemoryExtractionFailureStage.PERSISTENCE_BOUNDARY_VALIDATION,
                safeMessage,
                null,
                extractionResult.modelRequestId(),
                extractionResult.modelUsage(),
                extractionResult.modelDurationMs(),
                extractionResult.modelCallCount()
        );
    }
}