ALTER TABLE memory_item
    ADD COLUMN extraction_confidence DECIMAL(5, 4) NULL
        AFTER extraction_model_request_id,
    ADD COLUMN source_agent_run_id VARCHAR(128) COLLATE utf8mb4_bin NULL
        AFTER extraction_confidence,
    ADD CONSTRAINT chk_memory_item_extraction_confidence
        CHECK (
            extraction_confidence IS NULL
            OR extraction_confidence BETWEEN 0 AND 1
        );