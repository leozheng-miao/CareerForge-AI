ALTER TABLE training_plan
    ADD COLUMN generation_context_json JSON NULL
        AFTER gap_snapshot_id,

    ADD CONSTRAINT chk_training_plan_generation_context_json
        CHECK (
            generation_context_json IS NULL
            OR JSON_TYPE(generation_context_json) = 'OBJECT'
        );