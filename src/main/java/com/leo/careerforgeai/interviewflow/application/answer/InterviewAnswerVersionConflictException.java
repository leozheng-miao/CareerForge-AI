package com.leo.careerforgeai.interviewflow.application.answer;

/**
 * @program: CareerForge-AI
 * @description: 携带服务端当前答案版本的草稿CAS冲突
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
public final class InterviewAnswerVersionConflictException
        extends IllegalStateException {

    private final long currentVersion;

    public InterviewAnswerVersionConflictException(long currentVersion) {
        super("答案版本已变化，请恢复服务端草稿");
        if (currentVersion < 0) {
            throw new IllegalArgumentException(
                    "currentVersion不能小于0"
            );
        }
        this.currentVersion = currentVersion;
    }

    public long currentVersion() {
        return currentVersion;
    }
}
