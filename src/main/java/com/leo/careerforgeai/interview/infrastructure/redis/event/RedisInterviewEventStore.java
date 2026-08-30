package com.leo.careerforgeai.interview.infrastructure.redis.event;

import com.leo.careerforgeai.agent.config.CareerForgeRedisProperties;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureErrorType;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureException;
import com.leo.careerforgeai.agent.infrastructure.redis.key.CareerForgeRedisKeyFactory;
import com.leo.careerforgeai.interview.application.event.InterviewEvent;
import com.leo.careerforgeai.interview.application.event.InterviewEventType;
import com.leo.careerforgeai.interview.application.event.StoredInterviewEvent;
import com.leo.careerforgeai.interview.application.port.InterviewEventStore;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
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
 * @description: 使用严格白名单字段、原子裁剪和TTL保存模拟面试安全Redis Stream事件
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@Component
public class RedisInterviewEventStore implements InterviewEventStore {

    private static final String INTERVIEW_ID_FIELD = "interviewId";
    private static final String TYPE_FIELD = "type";
    private static final String STATUS_FIELD = "status";
    private static final String OCCURRED_AT_FIELD = "occurredAt";
    private static final int MAX_READ_LIMIT = 1_000;
    private static final Pattern EVENT_ID_PATTERN = Pattern.compile("\\d+-\\d+");
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            INTERVIEW_ID_FIELD,
            TYPE_FIELD,
            STATUS_FIELD,
            OCCURRED_AT_FIELD
    );

    private static final DefaultRedisScript<String> APPEND_SCRIPT = new DefaultRedisScript<>(
            """
            local eventId = redis.call(
                'XADD',
                KEYS[1],
                'MAXLEN',
                ARGV[1],
                '*',
                'interviewId',
                ARGV[2],
                'type',
                ARGV[3],
                'status',
                ARGV[4],
                'occurredAt',
                ARGV[5]
            )
            redis.call('PEXPIRE', KEYS[1], ARGV[6])
            return eventId
            """,
            String.class
    );

    private final StringRedisTemplate redisTemplate;
    private final CareerForgeRedisKeyFactory keyFactory;
    private final CareerForgeRedisProperties properties;

    public RedisInterviewEventStore(StringRedisTemplate redisTemplate,
                                    CareerForgeRedisKeyFactory keyFactory,
                                    CareerForgeRedisProperties properties) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate不能为空");
        this.keyFactory = Objects.requireNonNull(keyFactory, "keyFactory不能为空");
        this.properties = Objects.requireNonNull(properties, "properties不能为空");
    }

    @Override
    public String append(InterviewEvent event) {
        Objects.requireNonNull(event, "event不能为空");
        String key = keyFactory.interviewEventStreamKey(event.ownerId(), event.interviewId());

        try {
            String eventId = redisTemplate.execute(
                    APPEND_SCRIPT,
                    List.of(key),
                    Long.toString(properties.eventStreamMaxLength()),
                    event.interviewId().toString(),
                    event.type().name(),
                    event.status().name(),
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
            throw infrastructureFailure(
                    RedisInfrastructureErrorType.TIMED_OUT,
                    "Redis面试事件追加超时",
                    exception
            );
        } catch (RedisConnectionFailureException exception) {
            throw infrastructureFailure(
                    RedisInfrastructureErrorType.UNAVAILABLE,
                    "Redis面试事件存储不可用",
                    exception
            );
        } catch (DataAccessException exception) {
            throw infrastructureFailure(
                    RedisInfrastructureErrorType.COMMAND_FAILED,
                    "Redis面试事件追加失败",
                    exception
            );
        }
    }

    @Override
    public List<StoredInterviewEvent> readAfter(
            ActorId ownerId,
            UUID interviewId,
            String lastEventId,
            int limit
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        if (limit < 1 || limit > MAX_READ_LIMIT) {
            throw new IllegalArgumentException("limit必须在1到1000之间");
        }

        String key = keyFactory.interviewEventStreamKey(ownerId, interviewId);

        try {
            StreamOperations<String, String, String> operations = redisTemplate.opsForStream();
            List<MapRecord<String, String, String>> records = operations.range(
                    key,
                    eventRange(lastEventId),
                    Limit.limit().count(limit)
            );
            if (records == null) {
                throw new RedisInfrastructureException(
                        RedisInfrastructureErrorType.UNEXPECTED_RESPONSE,
                        "Redis面试事件读取返回空结果对象"
                );
            }
            return records.stream().map(record -> restore(record, interviewId)).toList();
        } catch (QueryTimeoutException exception) {
            throw infrastructureFailure(
                    RedisInfrastructureErrorType.TIMED_OUT,
                    "Redis面试事件读取超时",
                    exception
            );
        } catch (RedisConnectionFailureException exception) {
            throw infrastructureFailure(
                    RedisInfrastructureErrorType.UNAVAILABLE,
                    "Redis面试事件存储不可用",
                    exception
            );
        } catch (DataAccessException exception) {
            throw infrastructureFailure(
                    RedisInfrastructureErrorType.COMMAND_FAILED,
                    "Redis面试事件读取失败",
                    exception
            );
        } catch (IllegalArgumentException exception) {
            throw infrastructureFailure(
                    RedisInfrastructureErrorType.INVALID_DATA,
                    "Redis面试事件数据未通过安全校验",
                    exception
            );
        }
    }

    private Range<String> eventRange(String lastEventId) {
        if (lastEventId == null) return Range.unbounded();
        if (!EVENT_ID_PATTERN.matcher(lastEventId).matches()) {
            throw new IllegalArgumentException("lastEventId格式不合法");
        }
        return Range.of(
                Range.Bound.exclusive(lastEventId),
                Range.Bound.unbounded()
        );
    }

    private StoredInterviewEvent restore(
            MapRecord<String, String, String> record,
            UUID expectedInterviewId
    ) {
        Map<String, String> values = record.getValue();
        if (!values.keySet().equals(ALLOWED_FIELDS)) {
            throw new IllegalArgumentException("Redis面试事件字段集合不合法");
        }

        UUID storedInterviewId = UUID.fromString(requireField(values, INTERVIEW_ID_FIELD));
        if (!expectedInterviewId.equals(storedInterviewId)) {
            throw new IllegalArgumentException("Redis事件interviewId与Key身份不一致");
        }

        return new StoredInterviewEvent(
                record.getId().getValue(),
                storedInterviewId,
                InterviewEventType.valueOf(requireField(values, TYPE_FIELD)),
                InterviewStatus.valueOf(requireField(values, STATUS_FIELD)),
                Instant.parse(requireField(values, OCCURRED_AT_FIELD))
        );
    }

    private String requireField(Map<String, String> values, String field) {
        String value = values.get(field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Redis面试事件缺少字段: " + field);
        }
        return value;
    }

    private RedisInfrastructureException infrastructureFailure(
            RedisInfrastructureErrorType errorType,
            String message,
            Throwable cause
    ) {
        return new RedisInfrastructureException(errorType, message, cause);
    }
}