package com.leo.careerforgeai.memory.infrastructure.persistence.converter;

import com.leo.careerforgeai.memory.domain.profile.MemoryDecision;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecisionType;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.profile.MemorySource;
import com.leo.careerforgeai.memory.domain.profile.MemorySourceType;
import com.leo.careerforgeai.memory.domain.profile.MemoryStatus;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import com.leo.careerforgeai.memory.infrastructure.persistence.entity.MemoryDecisionEntity;
import com.leo.careerforgeai.memory.infrastructure.persistence.entity.MemoryItemEntity;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 在Memory领域对象和MyBatis-Plus数据库Entity之间执行受控转换
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Component
public class MemoryPersistenceConverter {

    private static final TypeReference<List<String>>
            STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final JsonMapper jsonMapper;

    public MemoryPersistenceConverter(JsonMapper jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(
                jsonMapper,
                "jsonMapper 不能为空"
        );
    }

    /** 将领域Memory转换为memory_item表Entity。 */
    public MemoryItemEntity toEntity(MemoryItem memoryItem) {
        Objects.requireNonNull(memoryItem, "memoryItem 不能为空");

        MemoryItemEntity entity = new MemoryItemEntity();
        entity.setMemoryId(memoryItem.memoryId().toString());
        entity.setOwnerId(memoryItem.ownerId().value());
        entity.setMemoryType(memoryItem.type().name());
        entity.setNormalizedKey(memoryItem.normalizedKey().value());
        entity.setNormalizationVersion(
                memoryItem.normalizedKey().normalizationVersion()
        );
        entity.setContent(memoryItem.content());
        entity.setContentHash(memoryItem.contentHash());
        entity.setMemoryStatus(memoryItem.status().name());
        entity.setSourceType(memoryItem.source().sourceType().name());
        entity.setSourceId(memoryItem.source().sourceId());
        entity.setSourceHash(memoryItem.source().sourceHash());
        entity.setExtractionModelRequestId(memoryItem.extractionModelRequestId());
        entity.setExtractionConfidence(memoryItem.extractionConfidence());
        entity.setSourceAgentRunId(memoryItem.sourceAgentRunId());
        entity.setEvidenceRefsJson(
                serializeEvidenceRefs(memoryItem.evidenceRefs())
        );
        entity.setSupersedesId(
                toNullableString(memoryItem.supersedesId())
        );
        entity.setVersion(memoryItem.version());
        entity.setCreatedAt(memoryItem.createdAt());
        entity.setUpdatedAt(memoryItem.updatedAt());

        return entity;
    }

    /**
     * 将数据库Entity还原为领域Memory。
     * 数据库中存在非法枚举、UUID、Hash或状态组合时直接失败。
     */
    public MemoryItem toDomain(MemoryItemEntity entity) {
        Objects.requireNonNull(entity, "entity 不能为空");

        return new MemoryItem(
                UUID.fromString(entity.getMemoryId()),
                new ActorId(entity.getOwnerId()),
                MemoryType.valueOf(entity.getMemoryType()),
                new MemoryNormalizedKey(
                        entity.getNormalizedKey(),
                        entity.getNormalizationVersion()
                ),
                entity.getContent(),
                entity.getContentHash(),
                MemoryStatus.valueOf(entity.getMemoryStatus()),
                new MemorySource(
                        MemorySourceType.valueOf(entity.getSourceType()),
                        entity.getSourceId(),
                        entity.getSourceHash()
                ),
                entity.getExtractionModelRequestId(),
                entity.getExtractionConfidence(),
                entity.getSourceAgentRunId(),
                deserializeEvidenceRefs(
                        entity.getEvidenceRefsJson()
                ),
                parseNullableUuid(entity.getSupersedesId()),
                requireVersion(entity.getVersion()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /** 将领域决策转换为memory_decision表Entity。 */
    public MemoryDecisionEntity toEntity(
            MemoryDecision decision
    ) {
        Objects.requireNonNull(decision, "decision 不能为空");

        MemoryDecisionEntity entity =
                new MemoryDecisionEntity();

        entity.setDecisionId(decision.decisionId().toString());
        entity.setMemoryId(decision.memoryId().toString());
        entity.setOwnerId(decision.ownerId().value());
        entity.setDecisionType(decision.decisionType().name());
        entity.setFromStatus(decision.fromStatus().name());
        entity.setToStatus(decision.toStatus().name());
        entity.setExpectedMemoryVersion(
                decision.expectedMemoryVersion()
        );
        entity.setReplacementMemoryId(
                toNullableString(decision.replacementMemoryId())
        );
        entity.setNote(decision.note());
        entity.setDecidedAt(decision.decidedAt());

        return entity;
    }

    /**
     * 将数据库决策Entity还原为领域决策。
     * 构造过程会重新验证状态转换和替代关系。
     */
    public MemoryDecision toDomain(
            MemoryDecisionEntity entity
    ) {
        Objects.requireNonNull(entity, "entity 不能为空");

        return new MemoryDecision(
                UUID.fromString(entity.getDecisionId()),
                UUID.fromString(entity.getMemoryId()),
                new ActorId(entity.getOwnerId()),
                MemoryDecisionType.valueOf(
                        entity.getDecisionType()
                ),
                MemoryStatus.valueOf(entity.getFromStatus()),
                MemoryStatus.valueOf(entity.getToStatus()),
                requireVersion(
                        entity.getExpectedMemoryVersion()
                ),
                parseNullableUuid(
                        entity.getReplacementMemoryId()
                ),
                entity.getNote(),
                entity.getDecidedAt()
        );
    }

    private String serializeEvidenceRefs(
            List<String> evidenceRefs
    ) {
        try {
            return jsonMapper.writeValueAsString(evidenceRefs);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "evidenceRefs序列化失败",
                    exception
            );
        }
    }

    private List<String> deserializeEvidenceRefs(
            String evidenceRefsJson
    ) {
        if (evidenceRefsJson == null
                || evidenceRefsJson.isBlank()) {
            throw new IllegalStateException(
                    "数据库evidenceRefsJson不能为空"
            );
        }

        try {
            return jsonMapper.readValue(
                    evidenceRefsJson,
                    STRING_LIST_TYPE
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "数据库evidenceRefsJson格式非法",
                    exception
            );
        }
    }

    private static UUID parseNullableUuid(String value) {
        if (value == null) {
            return null;
        }

        return UUID.fromString(value);
    }

    private static String toNullableString(UUID value) {
        return value == null ? null : value.toString();
    }

    private static long requireVersion(Long version) {
        if (version == null) {
            throw new IllegalStateException(
                    "数据库version不能为空"
            );
        }

        return version;
    }
}