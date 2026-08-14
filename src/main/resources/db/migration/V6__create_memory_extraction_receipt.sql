CREATE TABLE memory_extraction_receipt
(
    receipt_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id            VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    session_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    extractor_version   VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_fingerprint   CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_refs_json    JSON                                           NOT NULL,
    memory_ids_json     JSON                                           NOT NULL,
    model_request_id    VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    model_call_count    INT UNSIGNED                                   NOT NULL,
    input_tokens        BIGINT UNSIGNED                                NOT NULL,
    output_tokens       BIGINT UNSIGNED                                NOT NULL,
    total_tokens        BIGINT UNSIGNED                                NOT NULL,
    model_duration_ms   BIGINT UNSIGNED                                NOT NULL,
    created_at          DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (receipt_id),

    CONSTRAINT uk_memory_extraction_receipt_id_owner
        UNIQUE (receipt_id, owner_id),

    CONSTRAINT uk_memory_extraction_receipt_owner_input
        UNIQUE (
                owner_id,
                extractor_version,
                input_fingerprint
            ),

    CONSTRAINT chk_memory_extraction_receipt_model_calls
        CHECK (model_call_count BETWEEN 1 AND 2),

    CONSTRAINT chk_memory_extraction_receipt_source_refs
        CHECK (JSON_TYPE(source_refs_json) = 'ARRAY'),

    CONSTRAINT chk_memory_extraction_receipt_memory_ids
        CHECK (JSON_TYPE(memory_ids_json) = 'ARRAY'),

    CONSTRAINT fk_memory_extraction_receipt_session_owner
        FOREIGN KEY (session_id, owner_id)
            REFERENCES coaching_session (session_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_memory_extraction_receipt_owner_session_created (
        owner_id,
        session_id,
        created_at
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;