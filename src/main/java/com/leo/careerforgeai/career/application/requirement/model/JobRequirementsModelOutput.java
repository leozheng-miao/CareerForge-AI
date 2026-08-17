package com.leo.careerforgeai.career.application.requirement.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record JobRequirementsModelOutput(
        @NotBlank @Size(max = 200)
        String jobTitle,

        @NotNull @Size(max = 30)
        List<@NotBlank @Size(max = 500) String> programmingLanguages,
        @NotNull @Size(max = 30)
        List<@NotBlank @Size(max = 500) String> backendAndInfrastructureRequirements,
        @NotNull @Size(max = 30)
        List<@NotBlank @Size(max = 500) String> agentRequirements,
        @NotNull @Size(max = 30)
        List<@NotBlank @Size(max = 500) String> ragRequirements,
        @NotNull @Size(max = 30)
        List<@NotBlank @Size(max = 500) String> engineeringRequirements,
        @NotNull @Size(max = 30)
        List<@NotBlank @Size(max = 500) String> bonusQualifications,
        @NotNull @Size(max = 30)
        List<@NotBlank @Size(max = 500) String> responsibilities,
        @NotNull @Size(max = 30)
        List<@NotBlank @Size(max = 500) String> interviewTopics
        ) {
}