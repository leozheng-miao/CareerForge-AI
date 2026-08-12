package com.leo.careerforgeai.shared.actor;

public record ActorId(String value) {

    public static final int MAX_LENGTH = 128;

    public ActorId {
        if (value == null) {
            throw new IllegalArgumentException("actorId must not be null");
        }

        value = value.strip();

        if (value.isEmpty()) {
            throw new IllegalArgumentException("actorId must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("actorId must not exceed " + MAX_LENGTH + " characters");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("actorId must not contain control characters");
        }
    }
}