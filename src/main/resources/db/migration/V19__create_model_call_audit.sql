CREATE TABLE model_call_audit
(
    audit_id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL,
    started_at          DATETIME(6)                                         NOT NULL,
    task_type           VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin   NOT NULL,
    operation_type      VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin   NOT NULL,
    provider_id         VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin   NOT NULL,
    model_profile       VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin   NOT NULL,
    provider_model      VARCHAR(128)                                        NOT NULL,
    routing_version     VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL,
    price_version       VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin   NOT NULL,
    reasoning_mode      VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin   NOT NULL,
    fallback_call       TINYINT(1)                                          NOT NULL,
    outcome             VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin   NOT NULL,
    error_category      VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin   NULL,
    provider_request_id VARCHAR(128)                                        NULL,
    usage_status        VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin   NOT NULL,
    input_tokens        BIGINT UNSIGNED                                     NULL,
    output_tokens       BIGINT UNSIGNED                                     NULL,
    total_tokens        BIGINT UNSIGNED                                     NULL,
    duration_ms         BIGINT UNSIGNED                                     NOT NULL,
    trace_id            VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin   NULL,
    span_id             VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin   NULL,

    PRIMARY KEY (audit_id),

    CONSTRAINT chk_model_call_audit_operation
        CHECK (operation_type IN ('CHAT', 'STREAM', 'TOOL_CALLING'))

    CONSTRAINT chk_model_call_audit_fallback
        CHECK (fallback_call IN (0, 1)),

    CONSTRAINT chk_model_call_audit_outcome
        CHECK (
            (outcome = 'SUCCESS' AND error_category IS NULL)
                OR
            (outcome = 'FAILURE' AND error_category IS NOT NULL)
        ),

    CONSTRAINT chk_model_call_audit_usage
        CHECK (
            (usage_status = 'KNOWN' AND input_tokens IS NOT NULL
                AND output_tokens IS NOT NULL AND total_tokens IS NOT NULL)
                OR
            (usage_status = 'UNKNOWN' AND input_tokens IS NULL
                AND output_tokens IS NULL AND total_tokens IS NULL)
        ),

    INDEX idx_model_call_audit_started_task (started_at, task_type),
    INDEX idx_model_call_audit_provider_profile_started (provider_id, model_profile, started_at),
    INDEX idx_model_call_audit_outcome_started (outcome, error_category, started_at),
    INDEX idx_model_call_audit_trace (trace_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;