package com.leo.careerforgeai.model.domain;

import java.util.List;

public record ModelRequest(
        List<ModelMessage> messages,
        ModelOutputFormat outputFormat
) {
}