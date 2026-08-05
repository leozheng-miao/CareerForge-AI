package com.leo.careerforgeai.knowledge.api.dto;

import com.leo.careerforgeai.knowledge.domain.KnowledgeDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record RagQueryRequest(
            @NotBlank @Size(max = 2_000) String query,
            @Size(max = 2) Set<@NotNull KnowledgeDocumentType> documentTypes,
            @Size(max = 20) Set<@NotBlank @Size(max = 200) String> documentIds
    ) {
        public RagQueryRequest {
            documentTypes = immutableSet(documentTypes);
            documentIds = immutableSet(documentIds);
        }

        private static <T> Set<T> immutableSet(Set<T> values) {
            if (values == null) return Set.of();
            return Collections.unmodifiableSet(new LinkedHashSet<>(values));
        }
    }