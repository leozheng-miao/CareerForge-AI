ALTER TABLE interview_report_suggestion
    ADD COLUMN suggestion_payload_json JSON NULL
        AFTER suggestion_content,

    ADD CONSTRAINT chk_report_suggestion_payload
        CHECK (
            suggestion_payload_json IS NULL
                OR JSON_TYPE(suggestion_payload_json) = 'OBJECT'
        );


ALTER TABLE memory_item
DROP CHECK chk_memory_item_source_type,

    ADD CONSTRAINT chk_memory_item_source_type
        CHECK (
            source_type IN (
                            'CONVERSATION_TURN',
                            'AGENT_RUN',
                            'JOB_DOCUMENT',
                            'PROJECT_EVIDENCE',
                            'INTERVIEW_REPORT'
                )
        );