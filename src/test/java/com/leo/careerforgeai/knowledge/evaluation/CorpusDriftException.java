package com.leo.careerforgeai.knowledge.evaluation;

public class CorpusDriftException extends IllegalStateException {

    public CorpusDriftException(String message) {
        super(message);
    }

    public CorpusDriftException(String message, Throwable cause) {
        super(message, cause);
    }
}