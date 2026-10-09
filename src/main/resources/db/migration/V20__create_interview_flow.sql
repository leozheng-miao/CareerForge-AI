CREATE TABLE interview_flow_session
(
    interview_id                CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id                   VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    request_id                 CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    request_fingerprint        CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    interview_mode             VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    session_stage              VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    timer_state                VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    duration_seconds           BIGINT UNSIGNED                                NOT NULL,
    accumulated_answer_seconds BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    last_active_at             DATETIME(6)                                    NULL,
    current_topic_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    current_question_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    submitted_answer_count     INT UNSIGNED                                   NOT NULL DEFAULT 0,
    follow_up_count            INT UNSIGNED                                   NOT NULL DEFAULT 0,
    consecutive_follow_up_count INT UNSIGNED                                  NOT NULL DEFAULT 0,
    input_version              BIGINT UNSIGNED                                NOT NULL DEFAULT 1,
    plan_version               BIGINT UNSIGNED                                NOT NULL DEFAULT 1,
    version                    BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    started_at                 DATETIME(6)                                    NULL,
    finished_at                DATETIME(6)                                    NULL,
    failure_code               VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at                 DATETIME(6)                                    NOT NULL,
    updated_at                 DATETIME(6)                                    NOT NULL,
    active_owner_id            VARCHAR(128) COLLATE utf8mb4_bin
        GENERATED ALWAYS AS (
            CASE
                WHEN session_stage = 'INTERVIEWING' THEN owner_id
                ELSE NULL
            END
        ) STORED,

    PRIMARY KEY (interview_id),

    CONSTRAINT uk_interview_flow_session_id_owner
        UNIQUE (interview_id, owner_id),

    CONSTRAINT uk_interview_flow_session_owner_request
        UNIQUE (owner_id, request_id),

    CONSTRAINT uk_interview_flow_session_active_owner
        UNIQUE (active_owner_id),

    CONSTRAINT chk_interview_flow_session_request
        CHECK (
            (request_id IS NULL AND request_fingerprint IS NULL)
                OR
            (request_id IS NOT NULL AND request_fingerprint IS NOT NULL)
        ),

    CONSTRAINT chk_interview_flow_session_mode
        CHECK (interview_mode IN ('QUICK', 'COMPREHENSIVE')),

    CONSTRAINT chk_interview_flow_session_stage
        CHECK (
            session_stage IN (
                              'PREPARING',
                              'READY',
                              'INTERVIEWING',
                              'GENERATING_REPORT',
                              'COMPLETED',
                              'FAILED'
                )
        ),

    CONSTRAINT chk_interview_flow_session_timer_state
        CHECK (
            timer_state IN (
                            'NOT_STARTED',
                            'RUNNING',
                            'PAUSED',
                            'EXHAUSTED',
                            'STOPPED'
                )
        ),

    CONSTRAINT chk_interview_flow_session_duration
        CHECK (
            (interview_mode = 'QUICK' AND duration_seconds = 3600)
                OR
            (
                interview_mode = 'COMPREHENSIVE'
                    AND duration_seconds IN (3600, 5400)
            )
        ),

    CONSTRAINT chk_interview_flow_session_elapsed
        CHECK (accumulated_answer_seconds <= duration_seconds),

    CONSTRAINT chk_interview_flow_session_follow_up_budget
        CHECK (
            consecutive_follow_up_count <= 2
                AND consecutive_follow_up_count <= follow_up_count
                AND (
                    (
                        interview_mode = 'QUICK'
                            AND follow_up_count <= 4
                    )
                        OR
                    (
                        interview_mode = 'COMPREHENSIVE'
                            AND (
                                (
                                    duration_seconds = 3600
                                        AND follow_up_count <= 4
                                )
                                    OR
                                (
                                    duration_seconds = 5400
                                        AND follow_up_count <= 8
                                )
                            )
                    )
                )
        ),

    CONSTRAINT chk_interview_flow_session_versions
        CHECK (input_version >= 1 AND plan_version >= 1),

    CONSTRAINT chk_interview_flow_session_updated_at
        CHECK (updated_at >= created_at),

    CONSTRAINT chk_interview_flow_session_lifecycle
        CHECK (
            (
                session_stage IN (
                                  'PREPARING',
                                  'READY',
                                  'INTERVIEWING',
                                  'GENERATING_REPORT'
                    )
                    AND finished_at IS NULL
                    AND failure_code IS NULL
            )
                OR
            (
                session_stage = 'COMPLETED'
                    AND finished_at IS NOT NULL
                    AND finished_at >= created_at
                    AND failure_code IS NULL
            )
                OR
            (
                session_stage = 'FAILED'
                    AND finished_at IS NOT NULL
                    AND finished_at >= created_at
                    AND failure_code IS NOT NULL
            )
        ),

    INDEX idx_interview_flow_session_owner_stage_updated (
        owner_id,
        session_stage,
        updated_at,
        interview_id
    ),

    INDEX idx_interview_flow_session_stage_updated (
        session_stage,
        updated_at,
        interview_id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE interview_flow_input_snapshot
(
    input_snapshot_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    session_id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id                    VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    interview_direction         VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    job_description             TEXT                                           NULL,
    salary_range                VARCHAR(255)                                    NULL,
    personal_description        TEXT                                            NULL,
    confirmed_profile_json      JSON                                            NULL,
    profile_schema_version      INT UNSIGNED                                    NULL,
    difficulty                  VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    duration_seconds            BIGINT UNSIGNED                                 NOT NULL,
    input_version               BIGINT UNSIGNED                                 NOT NULL DEFAULT 1,
    version                     BIGINT UNSIGNED                                 NOT NULL DEFAULT 0,
    created_at                  DATETIME(6)                                     NOT NULL,
    updated_at                  DATETIME(6)                                     NOT NULL,

    PRIMARY KEY (input_snapshot_id),

    CONSTRAINT uk_interview_flow_input_snapshot_session
        UNIQUE (session_id),

    CONSTRAINT uk_interview_flow_input_snapshot_id_scope
        UNIQUE (input_snapshot_id, session_id, owner_id),

    CONSTRAINT chk_interview_flow_input_snapshot_direction
        CHECK (
            CHAR_LENGTH(TRIM(interview_direction)) BETWEEN 1 AND 64
        ),

    CONSTRAINT chk_interview_flow_input_snapshot_profile
        CHECK (
            (
                confirmed_profile_json IS NULL
                    AND profile_schema_version IS NULL
            )
                OR
            (
                confirmed_profile_json IS NOT NULL
                    AND JSON_TYPE(confirmed_profile_json) = 'OBJECT'
                    AND profile_schema_version >= 1
            )
        ),

    CONSTRAINT chk_interview_flow_input_snapshot_difficulty
        CHECK (
            difficulty IS NULL
                OR difficulty IN ('BASIC', 'STANDARD', 'CHALLENGE')
        ),

    CONSTRAINT chk_interview_flow_input_snapshot_duration
        CHECK (duration_seconds IN (3600, 5400)),

    CONSTRAINT chk_interview_flow_input_snapshot_input_version
        CHECK (input_version >= 1),

    CONSTRAINT chk_interview_flow_input_snapshot_updated_at
        CHECK (updated_at >= created_at),

    CONSTRAINT fk_interview_flow_input_snapshot_session
        FOREIGN KEY (session_id, owner_id)
            REFERENCES interview_flow_session (interview_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_interview_flow_input_snapshot_owner_updated (
        owner_id,
        updated_at,
        input_snapshot_id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE interview_flow_topic
(
    topic_id             CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    session_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id             VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    sequence_no          INT UNSIGNED                                   NOT NULL,
    topic_type           VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    objective            TEXT                                           NOT NULL,
    topic_status         VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    coverage_result_json JSON                                           NULL,
    summary_json         JSON                                           NULL,
    summary_version      BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    plan_version         BIGINT UNSIGNED                                NOT NULL,
    version              BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    created_at           DATETIME(6)                                    NOT NULL,
    updated_at           DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (topic_id),

    CONSTRAINT uk_interview_flow_topic_id_scope
        UNIQUE (topic_id, session_id, owner_id),

    CONSTRAINT uk_interview_flow_topic_session_sequence
        UNIQUE (session_id, sequence_no),

    CONSTRAINT chk_interview_flow_topic_sequence
        CHECK (sequence_no >= 1),

    CONSTRAINT chk_interview_flow_topic_type
        CHECK (CHAR_LENGTH(TRIM(topic_type)) BETWEEN 1 AND 64),

    CONSTRAINT chk_interview_flow_topic_objective
        CHECK (CHAR_LENGTH(TRIM(objective)) BETWEEN 1 AND 2000),

    CONSTRAINT chk_interview_flow_topic_status
        CHECK (
            topic_status IN (
                             'PLANNED',
                             'ACTIVE',
                             'COMPLETED',
                             'SKIPPED',
                             'INVALIDATED'
                )
        ),

    CONSTRAINT chk_interview_flow_topic_coverage
        CHECK (
            coverage_result_json IS NULL
                OR JSON_TYPE(coverage_result_json) = 'OBJECT'
        ),

    CONSTRAINT chk_interview_flow_topic_summary
        CHECK (
            (summary_json IS NULL AND summary_version = 0)
                OR
            (
                summary_json IS NOT NULL
                    AND JSON_TYPE(summary_json) = 'OBJECT'
                    AND summary_version >= 1
            )
        ),

    CONSTRAINT chk_interview_flow_topic_plan_version
        CHECK (plan_version >= 1),

    CONSTRAINT chk_interview_flow_topic_updated_at
        CHECK (updated_at >= created_at),

    CONSTRAINT fk_interview_flow_topic_session
        FOREIGN KEY (session_id, owner_id)
            REFERENCES interview_flow_session (interview_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_interview_flow_topic_owner_session_status (
        owner_id,
        session_id,
        topic_status,
        sequence_no
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE interview_flow_question
(
    question_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    session_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    topic_id             CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id             VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    parent_question_id   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    question_type        VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_order        INT UNSIGNED                                   NOT NULL,
    question_text        TEXT                                           NOT NULL,
    source_refs_json     JSON                                           NOT NULL,
    content_fingerprint  CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    question_status      VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_version         BIGINT UNSIGNED                                NOT NULL,
    version              BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    presented_at         DATETIME(6)                                    NULL,
    closed_at            DATETIME(6)                                    NULL,
    created_at           DATETIME(6)                                    NOT NULL,
    updated_at           DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (question_id),

    CONSTRAINT uk_interview_flow_question_id_scope
        UNIQUE (question_id, session_id, owner_id),

    CONSTRAINT uk_interview_flow_question_session_order
        UNIQUE (session_id, display_order),

    CONSTRAINT uk_interview_flow_question_session_fingerprint
        UNIQUE (session_id, content_fingerprint),

    CONSTRAINT chk_interview_flow_question_type
        CHECK (question_type IN ('MAIN', 'FOLLOW_UP')),

    CONSTRAINT chk_interview_flow_question_parent
        CHECK (
            (question_type = 'MAIN' AND parent_question_id IS NULL)
                OR
            (question_type = 'FOLLOW_UP' AND parent_question_id IS NOT NULL)
        ),

    CONSTRAINT chk_interview_flow_question_display_order
        CHECK (display_order >= 1),

    CONSTRAINT chk_interview_flow_question_text
        CHECK (CHAR_LENGTH(TRIM(question_text)) BETWEEN 1 AND 8000),

    CONSTRAINT chk_interview_flow_question_sources
        CHECK (
            JSON_TYPE(source_refs_json) = 'ARRAY'
                AND JSON_LENGTH(source_refs_json) BETWEEN 1 AND 20
        ),

    CONSTRAINT chk_interview_flow_question_status
        CHECK (
            question_status IN (
                                'RESERVED',
                                'PRESENTED',
                                'CLOSED',
                                'SKIPPED',
                                'INVALIDATED'
                )
        ),

    CONSTRAINT chk_interview_flow_question_plan_version
        CHECK (plan_version >= 1),

    CONSTRAINT chk_interview_flow_question_updated_at
        CHECK (updated_at >= created_at),

    CONSTRAINT chk_interview_flow_question_lifecycle
        CHECK (
            (
                question_status IN ('RESERVED', 'INVALIDATED')
                    AND presented_at IS NULL
                    AND closed_at IS NULL
            )
                OR
            (
                question_status = 'PRESENTED'
                    AND presented_at IS NOT NULL
                    AND closed_at IS NULL
            )
                OR
            (
                question_status IN ('CLOSED', 'SKIPPED')
                    AND presented_at IS NOT NULL
                    AND closed_at IS NOT NULL
                    AND closed_at >= presented_at
            )
        ),

    CONSTRAINT fk_interview_flow_question_topic
        FOREIGN KEY (topic_id, session_id, owner_id)
            REFERENCES interview_flow_topic (topic_id, session_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    CONSTRAINT fk_interview_flow_question_parent
        FOREIGN KEY (parent_question_id, session_id, owner_id)
            REFERENCES interview_flow_question (question_id, session_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_interview_flow_question_owner_session_status (
        owner_id,
        session_id,
        question_status,
        display_order
    ),

    INDEX idx_interview_flow_question_topic_order (
        topic_id,
        display_order
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE interview_flow_answer
(
    answer_id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    session_id                 CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    question_id                CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id                   VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    draft_text                 TEXT                                           NULL,
    draft_version              BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    frozen_answer              TEXT                                           NULL,
    answer_content_hash        CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    submit_request_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    submit_request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    answer_status              VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    turn_analysis_json         JSON                                           NULL,
    model_decision_json        JSON                                           NULL,
    final_decision             VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    submitted_at               DATETIME(6)                                    NULL,
    version                    BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    created_at                 DATETIME(6)                                    NOT NULL,
    updated_at                 DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (answer_id),

    CONSTRAINT uk_interview_flow_answer_id_scope
        UNIQUE (answer_id, session_id, owner_id),

    CONSTRAINT uk_interview_flow_answer_question
        UNIQUE (question_id),

    CONSTRAINT uk_interview_flow_answer_owner_request
        UNIQUE (owner_id, submit_request_id),

    CONSTRAINT chk_interview_flow_answer_draft
        CHECK (draft_text IS NULL OR CHAR_LENGTH(draft_text) <= 8000),

    CONSTRAINT chk_interview_flow_answer_frozen
        CHECK (
            frozen_answer IS NULL
                OR CHAR_LENGTH(TRIM(frozen_answer)) BETWEEN 1 AND 8000
        ),

    CONSTRAINT chk_interview_flow_answer_submit_request
        CHECK (
            (
                submit_request_id IS NULL
                    AND submit_request_fingerprint IS NULL
            )
                OR
            (
                submit_request_id IS NOT NULL
                    AND submit_request_fingerprint IS NOT NULL
            )
        ),

    CONSTRAINT chk_interview_flow_answer_status
        CHECK (
            answer_status IN (
                              'DRAFT',
                              'SUBMITTED',
                              'ANALYZING',
                              'CLOSED',
                              'DISCARDED'
                )
        ),

    CONSTRAINT chk_interview_flow_answer_analysis
        CHECK (
            turn_analysis_json IS NULL
                OR JSON_TYPE(turn_analysis_json) = 'OBJECT'
        ),

    CONSTRAINT chk_interview_flow_answer_model_decision
        CHECK (
            model_decision_json IS NULL
                OR JSON_TYPE(model_decision_json) = 'OBJECT'
        ),

    CONSTRAINT chk_interview_flow_answer_final_decision
        CHECK (
            final_decision IS NULL
                OR final_decision IN ('FOLLOW_UP', 'NEXT_TOPIC', 'FINISH')
        ),

    CONSTRAINT chk_interview_flow_answer_updated_at
        CHECK (updated_at >= created_at),

    CONSTRAINT chk_interview_flow_answer_lifecycle
        CHECK (
            (
                answer_status = 'DRAFT'
                    AND frozen_answer IS NULL
                    AND answer_content_hash IS NULL
                    AND submitted_at IS NULL
                    AND submit_request_id IS NULL
            )
                OR
            (
                answer_status IN ('SUBMITTED', 'ANALYZING', 'CLOSED')
                    AND frozen_answer IS NOT NULL
                    AND answer_content_hash IS NOT NULL
                    AND submitted_at IS NOT NULL
                    AND submit_request_id IS NOT NULL
            )
                OR
            (
                answer_status = 'DISCARDED'
                    AND frozen_answer IS NULL
                    AND answer_content_hash IS NULL
                    AND submitted_at IS NOT NULL
                    AND submit_request_id IS NOT NULL
            )
        ),

    CONSTRAINT fk_interview_flow_answer_question
        FOREIGN KEY (question_id, session_id, owner_id)
            REFERENCES interview_flow_question (question_id, session_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_interview_flow_answer_owner_session_updated (
        owner_id,
        session_id,
        updated_at,
        answer_id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE interview_flow_task
(
    task_id             CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    session_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id            VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    task_type           VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    business_key        VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    task_status         VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt_count       INT UNSIGNED                                   NOT NULL DEFAULT 0,
    max_attempts        INT UNSIGNED                                   NOT NULL,
    lease_owner         VARCHAR(128) COLLATE utf8mb4_bin               NULL,
    lease_until         DATETIME(6)                                    NULL,
    next_retry_at       DATETIME(6)                                    NULL,
    input_version       BIGINT UNSIGNED                                NOT NULL,
    plan_version        BIGINT UNSIGNED                                NOT NULL,
    context_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    last_error_code     VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    last_error_message  VARCHAR(500)                                   NULL,
    started_at          DATETIME(6)                                    NULL,
    completed_at        DATETIME(6)                                    NULL,
    version             BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    created_at          DATETIME(6)                                    NOT NULL,
    updated_at          DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (task_id),

    CONSTRAINT uk_interview_flow_task_id_scope
        UNIQUE (task_id, session_id, owner_id),

    CONSTRAINT uk_interview_flow_task_business_key
        UNIQUE (business_key),

    CONSTRAINT chk_interview_flow_task_type
        CHECK (
            task_type IN (
                          'PREPARE_INTERVIEW',
                          'ANALYZE_TURN',
                          'GENERATE_REPORT'
                )
        ),

    CONSTRAINT chk_interview_flow_task_status
        CHECK (
            task_status IN (
                            'PENDING',
                            'RUNNING',
                            'RETRY_WAIT',
                            'SUCCEEDED',
                            'FAILED'
                )
        ),

    CONSTRAINT chk_interview_flow_task_attempts
        CHECK (
            max_attempts >= 1
                AND attempt_count <= max_attempts
        ),

    CONSTRAINT chk_interview_flow_task_versions
        CHECK (input_version >= 1 AND plan_version >= 1),

    CONSTRAINT chk_interview_flow_task_lease
        CHECK (
            (
                task_status = 'RUNNING'
                    AND lease_owner IS NOT NULL
                    AND lease_until IS NOT NULL
            )
                OR
            (
                task_status <> 'RUNNING'
                    AND lease_owner IS NULL
                    AND lease_until IS NULL
            )
        ),

    CONSTRAINT chk_interview_flow_task_retry
        CHECK (
            (task_status = 'RETRY_WAIT' AND next_retry_at IS NOT NULL)
                OR
            (task_status <> 'RETRY_WAIT' AND next_retry_at IS NULL)
        ),

    CONSTRAINT chk_interview_flow_task_completion
        CHECK (
            (
                task_status IN ('PENDING', 'RUNNING', 'RETRY_WAIT')
                    AND completed_at IS NULL
            )
                OR
            (
                task_status IN ('SUCCEEDED', 'FAILED')
                    AND completed_at IS NOT NULL
                    AND completed_at >= created_at
            )
        ),

    CONSTRAINT chk_interview_flow_task_updated_at
        CHECK (updated_at >= created_at),

    CONSTRAINT fk_interview_flow_task_session
        FOREIGN KEY (session_id, owner_id)
            REFERENCES interview_flow_session (interview_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_interview_flow_task_claim (
        task_status,
        next_retry_at,
        lease_until,
        created_at,
        task_id
    ),

    INDEX idx_interview_flow_task_owner_status (
        owner_id,
        task_status,
        updated_at,
        task_id
    ),

    INDEX idx_interview_flow_task_lease_owner (
        lease_owner,
        lease_until,
        task_id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE interview_flow_report
(
    report_id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    session_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id             VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    report_status        VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    report_json          JSON                                           NULL,
    schema_version       INT UNSIGNED                                   NOT NULL,
    input_version        BIGINT UNSIGNED                                NOT NULL,
    plan_version         BIGINT UNSIGNED                                NOT NULL,
    context_fingerprint  CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    failure_code         VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    version              BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    created_at           DATETIME(6)                                    NOT NULL,
    updated_at           DATETIME(6)                                    NOT NULL,
    completed_at         DATETIME(6)                                    NULL,

    PRIMARY KEY (report_id),

    CONSTRAINT uk_interview_flow_report_id_scope
        UNIQUE (report_id, session_id, owner_id),

    CONSTRAINT uk_interview_flow_report_session
        UNIQUE (session_id),

    CONSTRAINT chk_interview_flow_report_status
        CHECK (
            report_status IN (
                              'PENDING',
                              'GENERATING',
                              'READY',
                              'FAILED'
                )
        ),

    CONSTRAINT chk_interview_flow_report_json
        CHECK (
            report_json IS NULL
                OR JSON_TYPE(report_json) = 'OBJECT'
        ),

    CONSTRAINT chk_interview_flow_report_versions
        CHECK (
            schema_version >= 1
                AND input_version >= 1
                AND plan_version >= 1
        ),

    CONSTRAINT chk_interview_flow_report_updated_at
        CHECK (updated_at >= created_at),

    CONSTRAINT chk_interview_flow_report_lifecycle
        CHECK (
            (
                report_status IN ('PENDING', 'GENERATING')
                    AND report_json IS NULL
                    AND completed_at IS NULL
                    AND failure_code IS NULL
            )
                OR
            (
                report_status = 'READY'
                    AND report_json IS NOT NULL
                    AND completed_at IS NOT NULL
                    AND completed_at >= created_at
                    AND failure_code IS NULL
            )
                OR
            (
                report_status = 'FAILED'
                    AND report_json IS NULL
                    AND completed_at IS NOT NULL
                    AND completed_at >= created_at
                    AND failure_code IS NOT NULL
            )
        ),

    CONSTRAINT fk_interview_flow_report_session
        FOREIGN KEY (session_id, owner_id)
            REFERENCES interview_flow_session (interview_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_interview_flow_report_owner_status (
        owner_id,
        report_status,
        updated_at,
        report_id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


ALTER TABLE interview_flow_session
    ADD CONSTRAINT fk_interview_flow_session_current_topic
        FOREIGN KEY (current_topic_id, interview_id, owner_id)
            REFERENCES interview_flow_topic (topic_id, session_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,
    ADD CONSTRAINT fk_interview_flow_session_current_question
        FOREIGN KEY (current_question_id, interview_id, owner_id)
            REFERENCES interview_flow_question (question_id, session_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT;
