ALTER TABLE memory_item
    ADD COLUMN extraction_model_request_id VARCHAR(128) COLLATE utf8mb4_bin NULL
        AFTER source_hash;