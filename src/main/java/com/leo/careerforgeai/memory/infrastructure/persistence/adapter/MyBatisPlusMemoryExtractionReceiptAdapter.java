package com.leo.careerforgeai.memory.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.careerforgeai.memory.application.port.extraction.MemoryExtractionReceiptRepository;
import com.leo.careerforgeai.memory.domain.extraction.MemoryExtractionInputIdentity;
import com.leo.careerforgeai.memory.domain.extraction.MemoryExtractionReceipt;
import com.leo.careerforgeai.memory.domain.extraction.MemoryExtractionSourceSnapshot;
import com.leo.careerforgeai.memory.infrastructure.persistence.entity.MemoryExtractionReceiptEntity;
import com.leo.careerforgeai.memory.infrastructure.persistence.mapper.MemoryExtractionReceiptMapper;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 使用MyBatis-Plus和MySQL JSON字段实现成功Memory提取凭证持久化
 * @author: Miao Zheng
 * @date: 2026-08-14
 **/
@Repository
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MyBatisPlusMemoryExtractionReceiptAdapter
        implements MemoryExtractionReceiptRepository {

    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final TypeReference<List<MemoryExtractionSourceSnapshot>>
            SOURCE_REFS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>>
            MEMORY_IDS_TYPE = new TypeReference<>() {
    };

    private final MemoryExtractionReceiptMapper mapper;
    private final JsonMapper jsonMapper;

    public MyBatisPlusMemoryExtractionReceiptAdapter(
            MemoryExtractionReceiptMapper mapper,
            JsonMapper jsonMapper
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper不能为空");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper不能为空");
    }

    @Override
    public void insert(MemoryExtractionReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt不能为空");
        int affectedRows = mapper.insert(toEntity(receipt));

        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "插入Memory提取凭证失败: affectedRows=" + affectedRows
            );
        }
    }

    @Override
    public Optional<MemoryExtractionReceipt> findByIdentity(
            ActorId ownerId,
            String extractorVersion,
            String inputFingerprint
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        String normalizedVersion = normalizeVersion(extractorVersion);

        if (inputFingerprint == null
                || !SHA256_PATTERN.matcher(inputFingerprint).matches()) {
            throw new IllegalArgumentException("inputFingerprint必须是小写SHA-256");
        }

        LambdaQueryWrapper<MemoryExtractionReceiptEntity> query =
                new LambdaQueryWrapper<>();

        query.eq(MemoryExtractionReceiptEntity::getOwnerId, ownerId.value())
                .eq(
                        MemoryExtractionReceiptEntity::getExtractorVersion,
                        normalizedVersion
                )
                .eq(
                        MemoryExtractionReceiptEntity::getInputFingerprint,
                        inputFingerprint
                );

        return Optional.ofNullable(mapper.selectOne(query))
                .map(this::toDomain);
    }

    private MemoryExtractionReceiptEntity toEntity(
            MemoryExtractionReceipt receipt
    ) {
        MemoryExtractionReceiptEntity entity =
                new MemoryExtractionReceiptEntity();

        entity.setReceiptId(receipt.receiptId().toString());
        entity.setOwnerId(receipt.ownerId().value());
        entity.setSessionId(receipt.inputIdentity().sessionId().toString());
        entity.setExtractorVersion(receipt.inputIdentity().extractorVersion());
        entity.setInputFingerprint(receipt.inputIdentity().inputFingerprint());
        entity.setSourceRefsJson(serialize(receipt.inputIdentity().sources()));
        entity.setMemoryIdsJson(serialize(
                receipt.memoryIds().stream().map(UUID::toString).toList()
        ));
        entity.setModelRequestId(receipt.modelRequestId());
        entity.setModelCallCount(receipt.modelCallCount());
        entity.setInputTokens(receipt.modelUsage().inputTokens());
        entity.setOutputTokens(receipt.modelUsage().outputTokens());
        entity.setTotalTokens(receipt.modelUsage().totalTokens());
        entity.setModelDurationMs(receipt.modelDurationMs());
        entity.setCreatedAt(receipt.createdAt());

        return entity;
    }

    private MemoryExtractionReceipt toDomain(
            MemoryExtractionReceiptEntity entity
    ) {
        Objects.requireNonNull(entity, "entity不能为空");

        List<MemoryExtractionSourceSnapshot> sources =
                deserializeSources(entity.getSourceRefsJson());

        MemoryExtractionInputIdentity inputIdentity =
                new MemoryExtractionInputIdentity(
                        UUID.fromString(entity.getSessionId()),
                        entity.getExtractorVersion(),
                        entity.getInputFingerprint(),
                        sources
                );

        return new MemoryExtractionReceipt(
                UUID.fromString(entity.getReceiptId()),
                new ActorId(entity.getOwnerId()),
                inputIdentity,
                deserializeMemoryIds(entity.getMemoryIdsJson()),
                entity.getModelRequestId(),
                new ModelUsage(
                        requireLong(entity.getInputTokens(), "inputTokens"),
                        requireLong(entity.getOutputTokens(), "outputTokens"),
                        requireLong(entity.getTotalTokens(), "totalTokens")
                ),
                requireLong(entity.getModelDurationMs(), "modelDurationMs"),
                requireInteger(entity.getModelCallCount(), "modelCallCount"),
                Objects.requireNonNull(entity.getCreatedAt(), "数据库createdAt不能为空")
        );
    }

    private String serialize(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Memory提取凭证JSON序列化失败", exception);
        }
    }

    private List<MemoryExtractionSourceSnapshot> deserializeSources(
            String json
    ) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("数据库sourceRefsJson不能为空");
        }

        try {
            return jsonMapper.readValue(json, SOURCE_REFS_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("数据库sourceRefsJson格式非法", exception);
        }
    }

    private List<UUID> deserializeMemoryIds(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("数据库memoryIdsJson不能为空");
        }

        try {
            return jsonMapper.readValue(json, MEMORY_IDS_TYPE)
                    .stream()
                    .map(UUID::fromString)
                    .toList();
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("数据库memoryIdsJson格式非法", exception);
        }
    }

    private static String normalizeVersion(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("extractorVersion不能为空");
        }

        String normalized = value.strip();
        if (normalized.length() > 64
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("extractorVersion格式不合法");
        }
        return normalized;
    }

    private static long requireLong(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("数据库" + fieldName + "不能为空");
        }
        return value;
    }

    private static int requireInteger(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("数据库" + fieldName + "不能为空");
        }
        return value;
    }
}