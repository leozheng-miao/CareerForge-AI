package com.leo.careerforgeai.agent.infrastructure.redis.event;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunEventStore;
import com.leo.careerforgeai.agent.application.run.event.CoachingRunEvent;
import com.leo.careerforgeai.agent.application.run.event.CoachingRunEventType;
import com.leo.careerforgeai.agent.application.run.event.StoredCoachingRunEvent;
import com.leo.careerforgeai.agent.config.CareerForgeRedisProperties;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureErrorType;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureException;
import com.leo.careerforgeai.agent.infrastructure.redis.key.CareerForgeRedisKeyFactory;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 使用字符串序列化、原子TTL和严格白名单字段保存安全Run事件
 * @author: Miao Zheng
 * @date: 2026-08-23
 */
@Component
public class RedisCoachingRunEventStore implements CoachingRunEventStore {

    private static final String RUN_ID_FIELD = "runId";
    private static final String TYPE_FIELD = "type";
    private static final String STATUS_FIELD = "status";
    private static final String TOOL_NAME_FIELD = "toolName";
    private static final String TOOL_STATUS_FIELD = "toolStatus";
    private static final String OCCURRED_AT_FIELD = "occurredAt";
    private static final int MAX_READ_LIMIT = 1_000;
    private static final Pattern EVENT_ID_PATTERN = Pattern.compile("\\d+-\\d+");
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            RUN_ID_FIELD,
            TYPE_FIELD,
            STATUS_FIELD,
            TOOL_NAME_FIELD,
            TOOL_STATUS_FIELD,
            OCCURRED_AT_FIELD
    );

    private static final DefaultRedisScript<String> APPEND_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local eventId = redis.call(
                        'XADD',
                        KEYS[1],
                        'MAXLEN',
                        ARGV[1],
                        '*',
                        'runId',
                        ARGV[2],
                        'type',
                        ARGV[3],
                        'status',
                        ARGV[4],
                        'toolName',
                        ARGV[5],
                        'toolStatus',
                        ARGV[6],
                        'occurredAt',
                        ARGV[7]
                    )
                    redis.call('PEXPIRE', KEYS[1], ARGV[8])
                    return eventId
                    """,
                    String.class
            );

    private final StringRedisTemplate redisTemplate;
    private final CareerForgeRedisKeyFactory keyFactory;
    private final CareerForgeRedisProperties properties;

    public RedisCoachingRunEventStore(
            StringRedisTemplate redisTemplate,
            CareerForgeRedisKeyFactory keyFactory,
            CareerForgeRedisProperties properties
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate不能为空");
        this.keyFactory = Objects.requireNonNull(keyFactory, "keyFactory不能为空");
        this.properties = Objects.requireNonNull(properties, "properties不能为空");
    }

    @Override
    public String append(CoachingRunEvent event) {
        Objects.requireNonNull(event, "event不能为空");
        String key = keyFactory.runEventStreamKey(event.ownerId(), event.runId());

        try {
            String eventId = redisTemplate.execute(
                    APPEND_SCRIPT,
                    List.of(key),
                    Long.toString(properties.eventStreamMaxLength()),
                    event.runId().toString(),
                    event.type().name(),
                    event.status().name(),
                    emptyIfNull(event.toolName()),
                    event.toolStatus() == null ? "" : event.toolStatus().name(),
                    event.occurredAt().toString(),
                    Long.toString(properties.eventTtl().toMillis())
            );
            if (eventId == null || !EVENT_ID_PATTERN.matcher(eventId).matches()) {
                throw new RedisInfrastructureException(
                        RedisInfrastructureErrorType.UNEXPECTED_RESPONSE,
                        "Redis未返回合法Stream事件ID"
                );
            }
            return eventId;
        } catch (QueryTimeoutException exception) {
            throw infrastructureFailure(RedisInfrastructureErrorType.TIMED_OUT, "Redis事件追加超时", exception);
        } catch (RedisConnectionFailureException exception) {
            throw infrastructureFailure(RedisInfrastructureErrorType.UNAVAILABLE, "Redis事件存储不可用", exception);
        } catch (DataAccessException exception) {
            throw infrastructureFailure(RedisInfrastructureErrorType.COMMAND_FAILED, "Redis事件追加失败", exception);
        }
    }

    @Override
    public List<StoredCoachingRunEvent> readAfter(
            ActorId ownerId,
            UUID runId,
            String lastEventId,
            int limit
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(runId, "runId不能为空");
        if (limit < 1 || limit > MAX_READ_LIMIT) {
            throw new IllegalArgumentException("limit必须在1到1000之间");
        }

        Range<String> range = eventRange(lastEventId);
        String key = keyFactory.runEventStreamKey(ownerId, runId);

        try {
            StreamOperations<String, String, String> operations = redisTemplate.opsForStream();
            List<MapRecord<String, String, String>> records = operations.range(
                    key,
                    range,
                    Limit.limit().count(limit)
            );
            if (records == null) {
                throw new RedisInfrastructureException(
                        RedisInfrastructureErrorType.UNEXPECTED_RESPONSE,
                        "Redis事件读取返回空结果对象"
                );
            }
            return records.stream().map(record -> restore(record, runId)).toList();
        } catch (QueryTimeoutException exception) {
            throw infrastructureFailure(RedisInfrastructureErrorType.TIMED_OUT, "Redis事件读取超时", exception);
        } catch (RedisConnectionFailureException exception) {
            throw infrastructureFailure(RedisInfrastructureErrorType.UNAVAILABLE, "Redis事件存储不可用", exception);
        } catch (DataAccessException exception) {
            throw infrastructureFailure(RedisInfrastructureErrorType.COMMAND_FAILED, "Redis事件读取失败", exception);
        } catch (IllegalArgumentException exception) {
            throw infrastructureFailure(
                    RedisInfrastructureErrorType.INVALID_DATA,
                    "Redis事件数据未通过安全校验",
                    exception
            );
        }
    }

    private static Range<String> eventRange(String lastEventId) {
        if (lastEventId == null) return Range.unbounded();
        if (!EVENT_ID_PATTERN.matcher(lastEventId).matches()) {
            throw new IllegalArgumentException("lastEventId格式不合法");
        }
        return Range.of(Range.Bound.exclusive(lastEventId), Range.Bound.unbounded());
    }

    private static StoredCoachingRunEvent restore(
            MapRecord<String, String, String> record,
            UUID expectedRunId
    ) {
        Map<String, String> values = record.getValue();
        if (!values.keySet().equals(ALLOWED_FIELDS)) {
            throw new IllegalArgumentException("Redis事件字段集合不合法");
        }

        UUID storedRunId = UUID.fromString(requireField(values, RUN_ID_FIELD));
        if (!expectedRunId.equals(storedRunId)) {
            throw new IllegalArgumentException("Redis事件runId与Key身份不一致");
        }

        String toolName = optionalField(values, TOOL_NAME_FIELD);
        String toolStatusValue = optionalField(values, TOOL_STATUS_FIELD);

        return new StoredCoachingRunEvent(
                record.getId().getValue(),
                storedRunId,
                CoachingRunEventType.valueOf(requireField(values, TYPE_FIELD)),
                CoachingRunStatus.valueOf(requireField(values, STATUS_FIELD)),
                toolName,
                toolStatusValue == null ? null : ToolExecutionStatus.valueOf(toolStatusValue),
                Instant.parse(requireField(values, OCCURRED_AT_FIELD))
        );
    }

    private static String requireField(Map<String, String> values, String field) {
        String value = values.get(field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Redis事件缺少字段: " + field);
        }
        return value;
    }

    private static String optionalField(Map<String, String> values, String field) {
        String value = values.get(field);
        if (value == null) throw new IllegalArgumentException("Redis事件缺少字段: " + field);
        return value.isEmpty() ? null : value;
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private static RedisInfrastructureException infrastructureFailure(
            RedisInfrastructureErrorType errorType,
            String message,
            Throwable cause
    ) {
        return new RedisInfrastructureException(errorType, message, cause);
    }
}