ALTER TABLE interview_flow_topic
    DROP INDEX uk_interview_flow_topic_session_sequence,
    ADD CONSTRAINT uk_interview_flow_topic_session_plan_sequence
        UNIQUE (session_id, plan_version, sequence_no);

ALTER TABLE interview_flow_question
    DROP INDEX uk_interview_flow_question_session_order,
    DROP INDEX uk_interview_flow_question_session_fingerprint,
    ADD CONSTRAINT uk_interview_flow_question_session_plan_order
        UNIQUE (session_id, plan_version, display_order),
    ADD CONSTRAINT uk_interview_flow_question_session_plan_fingerprint
        UNIQUE (session_id, plan_version, content_fingerprint);
