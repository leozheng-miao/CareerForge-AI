ALTER TABLE interview_answer
    ADD CONSTRAINT uk_interview_answer_review_scope
        UNIQUE (answer_id, interview_id, round_id, question_id, owner_id);


CREATE TABLE interview_technical_review
(
    technical_review_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    interview_id              CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    round_id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    question_id               CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    answer_id                 CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id                  VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    dimension_scores_json     JSON                                           NOT NULL,
    covered_points_json       JSON                                           NOT NULL,
    errors_or_omissions_json  JSON                                           NOT NULL,
    verification_basis_json   JSON                                           NOT NULL,
    suggested_follow_up       VARCHAR(2000)                                  NOT NULL,
    model_request_id          VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    prompt_version            VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_hash                CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    output_hash               CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at                DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (technical_review_id),

    CONSTRAINT uk_technical_review_id_owner
        UNIQUE (technical_review_id, owner_id),

    CONSTRAINT uk_technical_review_answer
        UNIQUE (answer_id),

    CONSTRAINT chk_technical_review_dimension_scores
        CHECK (JSON_TYPE(dimension_scores_json) = 'OBJECT'
            AND JSON_LENGTH(dimension_scores_json) BETWEEN 1 AND 10),

    CONSTRAINT chk_technical_review_covered_points
        CHECK (JSON_TYPE(covered_points_json) = 'ARRAY'
            AND JSON_LENGTH(covered_points_json) <= 20),

    CONSTRAINT chk_technical_review_errors
        CHECK (JSON_TYPE(errors_or_omissions_json) = 'ARRAY'
            AND JSON_LENGTH(errors_or_omissions_json) <= 20),

    CONSTRAINT chk_technical_review_verification_basis
        CHECK (JSON_TYPE(verification_basis_json) = 'ARRAY'
            AND JSON_LENGTH(verification_basis_json) <= 20),

    CONSTRAINT chk_technical_review_follow_up
        CHECK (CHAR_LENGTH(suggested_follow_up) <= 2000),

    CONSTRAINT fk_technical_review_answer
        FOREIGN KEY (answer_id, interview_id, round_id, question_id, owner_id)
            REFERENCES interview_answer (answer_id, interview_id, round_id, question_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_technical_review_owner_interview (
        owner_id,
        interview_id,
        created_at,
        technical_review_id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE interview_evidence_review
(
    evidence_review_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    interview_id                CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    round_id                    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    question_id                 CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    answer_id                   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id                    VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    review_source               VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    verdict                     VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_reference_ids_json JSON                                           NOT NULL,
    reason                      VARCHAR(2000)                                  NOT NULL,
    model_request_id            VARCHAR(128) COLLATE utf8mb4_bin               NULL,
    prompt_version              VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    input_hash                  CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    output_hash                 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at                  DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (evidence_review_id),

    CONSTRAINT uk_evidence_review_id_owner
        UNIQUE (evidence_review_id, owner_id),

    CONSTRAINT uk_evidence_review_answer
        UNIQUE (answer_id),

    CONSTRAINT chk_evidence_review_source
        CHECK (review_source IN ('MODEL', 'JAVA')),

    CONSTRAINT chk_evidence_review_verdict
        CHECK (verdict IN (
                           'SUPPORTED',
                           'PARTIALLY_SUPPORTED',
                           'UNSUPPORTED',
                           'CONTRADICTED',
                           'NOT_APPLICABLE'
            )),

    CONSTRAINT chk_evidence_review_references
        CHECK (JSON_TYPE(evidence_reference_ids_json) = 'ARRAY'
            AND JSON_LENGTH(evidence_reference_ids_json) <= 10),

    CONSTRAINT chk_evidence_review_required_references
        CHECK (
            verdict NOT IN ('SUPPORTED', 'PARTIALLY_SUPPORTED', 'CONTRADICTED')
                OR JSON_LENGTH(evidence_reference_ids_json) >= 1
            ),

    CONSTRAINT chk_evidence_review_not_applicable
        CHECK (verdict <> 'NOT_APPLICABLE'
            OR JSON_LENGTH(evidence_reference_ids_json) = 0),

    CONSTRAINT chk_evidence_review_reason
        CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 2000),

    CONSTRAINT chk_evidence_review_provenance
        CHECK (
            (review_source = 'MODEL'
                AND model_request_id IS NOT NULL
                AND prompt_version IS NOT NULL)
                OR
            (review_source = 'JAVA'
                AND verdict = 'NOT_APPLICABLE'
                AND JSON_LENGTH(evidence_reference_ids_json) = 0
                AND model_request_id IS NULL
                AND prompt_version IS NULL)
            ),

    CONSTRAINT fk_evidence_review_answer
        FOREIGN KEY (answer_id, interview_id, round_id, question_id, owner_id)
            REFERENCES interview_answer (answer_id, interview_id, round_id, question_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_evidence_review_owner_interview (
        owner_id,
        interview_id,
        created_at,
        evidence_review_id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE interview_report
(
    report_id                     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    interview_id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id                      VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    report_version                BIGINT UNSIGNED                                NOT NULL,
    report_status                 VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    strengths_json                JSON                                           NOT NULL,
    technical_gaps_json           JSON                                           NOT NULL,
    evidence_expression_risks_json JSON                                          NOT NULL,
    improvement_actions_json      JSON                                           NOT NULL,
    model_request_id              VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    prompt_version                VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_hash                    CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    output_hash                   CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version                       BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    created_at                    DATETIME(6)                                    NOT NULL,
    updated_at                    DATETIME(6)                                    NOT NULL,
    decided_at                    DATETIME(6)                                    NULL,

    PRIMARY KEY (report_id),

    CONSTRAINT uk_interview_report_id_owner
        UNIQUE (report_id, owner_id),

    CONSTRAINT uk_interview_report_scope
        UNIQUE (report_id, interview_id, owner_id),

    CONSTRAINT uk_interview_report_version
        UNIQUE (interview_id, report_version),

    CONSTRAINT chk_interview_report_business_version
        CHECK (report_version >= 1),

    CONSTRAINT chk_interview_report_status
        CHECK (report_status IN ('PENDING_CONFIRMATION', 'DECIDED')),

    CONSTRAINT chk_interview_report_strengths
        CHECK (JSON_TYPE(strengths_json) = 'ARRAY'
            AND JSON_LENGTH(strengths_json) <= 20),

    CONSTRAINT chk_interview_report_technical_gaps
        CHECK (JSON_TYPE(technical_gaps_json) = 'ARRAY'
            AND JSON_LENGTH(technical_gaps_json) <= 20),

    CONSTRAINT chk_interview_report_evidence_risks
        CHECK (JSON_TYPE(evidence_expression_risks_json) = 'ARRAY'
            AND JSON_LENGTH(evidence_expression_risks_json) <= 20),

    CONSTRAINT chk_interview_report_actions
        CHECK (JSON_TYPE(improvement_actions_json) = 'ARRAY'
            AND JSON_LENGTH(improvement_actions_json) BETWEEN 1 AND 20),

    CONSTRAINT chk_interview_report_updated_at
        CHECK (updated_at >= created_at),

    CONSTRAINT chk_interview_report_lifecycle
        CHECK (
            (report_status = 'PENDING_CONFIRMATION' AND decided_at IS NULL)
                OR
            (report_status = 'DECIDED'
                AND decided_at IS NOT NULL
                AND decided_at >= created_at)
            ),

    CONSTRAINT fk_interview_report_session
        FOREIGN KEY (interview_id, owner_id)
            REFERENCES mock_interview_session (interview_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_interview_report_owner_status_updated (
        owner_id,
        report_status,
        updated_at,
        report_id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE interview_report_suggestion
(
    suggestion_id      CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    report_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    interview_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id           VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    suggestion_type    VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    suggestion_order   SMALLINT UNSIGNED                              NOT NULL,
    suggestion_content VARCHAR(1000)                                  NOT NULL,
    content_hash       CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at         DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (suggestion_id),

    CONSTRAINT uk_report_suggestion_scope
        UNIQUE (suggestion_id, report_id, interview_id, owner_id),

    CONSTRAINT uk_report_suggestion_order
        UNIQUE (report_id, suggestion_type, suggestion_order),

    CONSTRAINT uk_report_suggestion_content
        UNIQUE (report_id, suggestion_type, content_hash),

    CONSTRAINT chk_report_suggestion_type
        CHECK (suggestion_type IN (
                                   'MEMORY_CANDIDATE',
                                   'TRAINING_PLAN_ADJUSTMENT'
            )),

    CONSTRAINT chk_report_suggestion_order
        CHECK (suggestion_order BETWEEN 1 AND 10),

    CONSTRAINT chk_report_suggestion_content
        CHECK (CHAR_LENGTH(TRIM(suggestion_content)) BETWEEN 1 AND 1000),

    CONSTRAINT fk_report_suggestion_report
        FOREIGN KEY (report_id, interview_id, owner_id)
            REFERENCES interview_report (report_id, interview_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_report_suggestion_owner_report (
        owner_id,
        report_id,
        suggestion_type,
        suggestion_order
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE interview_report_confirmation
(
    confirmation_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    report_id             CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    interview_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id              VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    request_id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_fingerprint   CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expected_report_version BIGINT UNSIGNED                              NOT NULL,
    confirmation_status   VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    failure_code          VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    version               BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    created_at            DATETIME(6)                                    NOT NULL,
    updated_at            DATETIME(6)                                    NOT NULL,
    application_finished_at DATETIME(6)                                  NULL,

    PRIMARY KEY (confirmation_id),

    CONSTRAINT uk_report_confirmation_scope
        UNIQUE (confirmation_id, report_id, interview_id, owner_id),

    CONSTRAINT uk_report_confirmation_report
        UNIQUE (report_id),

    CONSTRAINT uk_report_confirmation_owner_request
        UNIQUE (owner_id, request_id),

    CONSTRAINT chk_report_confirmation_status
        CHECK (confirmation_status IN (
                                       'PENDING_APPLICATION',
                                       'APPLIED',
                                       'PARTIALLY_APPLIED',
                                       'FAILED'
            )),

    CONSTRAINT chk_report_confirmation_updated_at
        CHECK (updated_at >= created_at),

    CONSTRAINT chk_report_confirmation_lifecycle
        CHECK (
            (confirmation_status = 'PENDING_APPLICATION'
                AND failure_code IS NULL
                AND application_finished_at IS NULL)
                OR
            (confirmation_status = 'APPLIED'
                AND failure_code IS NULL
                AND application_finished_at IS NOT NULL
                AND application_finished_at >= created_at)
                OR
            (confirmation_status IN ('PARTIALLY_APPLIED', 'FAILED')
                AND failure_code IS NOT NULL
                AND application_finished_at IS NOT NULL
                AND application_finished_at >= created_at)
            ),

    CONSTRAINT fk_report_confirmation_report
        FOREIGN KEY (report_id, interview_id, owner_id)
            REFERENCES interview_report (report_id, interview_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_report_confirmation_owner_status (
        owner_id,
        confirmation_status,
        updated_at,
        confirmation_id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE interview_report_decision
(
    decision_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    confirmation_id    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    suggestion_id      CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    report_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    interview_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id           VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    decision_type      VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    application_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    output_reference_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    failure_code       VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at         DATETIME(6)                                    NOT NULL,
    updated_at         DATETIME(6)                                    NOT NULL,
    finished_at        DATETIME(6)                                    NULL,

    PRIMARY KEY (decision_id),

    CONSTRAINT uk_report_decision_suggestion
        UNIQUE (suggestion_id),

    CONSTRAINT uk_report_decision_confirmation_suggestion
        UNIQUE (confirmation_id, suggestion_id),

    CONSTRAINT chk_report_decision_type
        CHECK (decision_type IN ('CONFIRMED', 'REJECTED')),

    CONSTRAINT chk_report_decision_application_status
        CHECK (application_status IN ('PENDING', 'APPLIED', 'REJECTED', 'FAILED')),

    CONSTRAINT chk_report_decision_updated_at
        CHECK (updated_at >= created_at),

    CONSTRAINT chk_report_decision_lifecycle
        CHECK (
            (decision_type = 'CONFIRMED'
                AND application_status = 'PENDING'
                AND output_reference_id IS NULL
                AND failure_code IS NULL
                AND finished_at IS NULL)
                OR
            (decision_type = 'CONFIRMED'
                AND application_status = 'APPLIED'
                AND output_reference_id IS NOT NULL
                AND failure_code IS NULL
                AND finished_at IS NOT NULL
                AND finished_at >= created_at)
                OR
            (decision_type = 'CONFIRMED'
                AND application_status = 'FAILED'
                AND output_reference_id IS NULL
                AND failure_code IS NOT NULL
                AND finished_at IS NOT NULL
                AND finished_at >= created_at)
                OR
            (decision_type = 'REJECTED'
                AND application_status = 'REJECTED'
                AND output_reference_id IS NULL
                AND failure_code IS NULL
                AND finished_at IS NOT NULL
                AND finished_at >= created_at)
            ),

    CONSTRAINT fk_report_decision_confirmation
        FOREIGN KEY (confirmation_id, report_id, interview_id, owner_id)
            REFERENCES interview_report_confirmation (
                                                      confirmation_id,
                                                      report_id,
                                                      interview_id,
                                                      owner_id
                )
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    CONSTRAINT fk_report_decision_suggestion
        FOREIGN KEY (suggestion_id, report_id, interview_id, owner_id)
            REFERENCES interview_report_suggestion (
                                                    suggestion_id,
                                                    report_id,
                                                    interview_id,
                                                    owner_id
                )
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_report_decision_owner_confirmation (
        owner_id,
        confirmation_id,
        application_status,
        decision_id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE interview_node_execution
(
    execution_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    interview_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id            VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    round_no            INT UNSIGNED                                   NOT NULL,
    node_name           VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_hash          CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    execution_status    VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    output_reference_id VARCHAR(128) COLLATE utf8mb4_bin               NULL,
    model_request_id    VARCHAR(128) COLLATE utf8mb4_bin               NULL,
    attempt_count       INT UNSIGNED                                   NOT NULL,
    model_call_count    INT UNSIGNED                                   NOT NULL DEFAULT 0,
    input_tokens        BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    output_tokens       BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    total_tokens        BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    model_duration_ms   BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    failure_code        VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    version             BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    started_at          DATETIME(6)                                    NOT NULL,
    finished_at         DATETIME(6)                                    NULL,
    created_at          DATETIME(6)                                    NOT NULL,
    updated_at          DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (execution_id),

    CONSTRAINT uk_node_execution_identity
        UNIQUE (interview_id, round_no, node_name, input_hash),

    CONSTRAINT chk_node_execution_round
        CHECK (round_no >= 0),

    CONSTRAINT chk_node_execution_name
        CHECK (CHAR_LENGTH(TRIM(node_name)) BETWEEN 1 AND 64),

    CONSTRAINT chk_node_execution_status
        CHECK (execution_status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),

    CONSTRAINT chk_node_execution_attempt
        CHECK (attempt_count >= 1),

    CONSTRAINT chk_node_execution_model_calls
        CHECK (model_call_count BETWEEN 0 AND 2),

    CONSTRAINT chk_node_execution_tokens
        CHECK (total_tokens = input_tokens + output_tokens),

    CONSTRAINT chk_node_execution_model_usage
        CHECK (
            (model_call_count = 0
                AND model_request_id IS NULL
                AND input_tokens = 0
                AND output_tokens = 0
                AND total_tokens = 0
                AND model_duration_ms = 0)
                OR
            (model_call_count >= 1 AND model_request_id IS NOT NULL)
            ),

    CONSTRAINT chk_node_execution_updated_at
        CHECK (updated_at >= created_at),

    CONSTRAINT chk_node_execution_lifecycle
        CHECK (
            (execution_status = 'RUNNING'
                AND output_reference_id IS NULL
                AND finished_at IS NULL)
                OR
            (execution_status = 'SUCCEEDED'
                AND output_reference_id IS NOT NULL
                AND finished_at IS NOT NULL
                AND finished_at >= started_at)
                OR
            (execution_status = 'FAILED'
                AND output_reference_id IS NULL
                AND failure_code IS NOT NULL
                AND finished_at IS NOT NULL
                AND finished_at >= started_at)
            ),

    CONSTRAINT fk_node_execution_session
        FOREIGN KEY (interview_id, owner_id)
            REFERENCES mock_interview_session (interview_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_node_execution_owner_status_updated (
        owner_id,
        execution_status,
        updated_at,
        execution_id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;