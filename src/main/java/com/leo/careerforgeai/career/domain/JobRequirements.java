package com.leo.careerforgeai.career.domain;

import java.util.List;

public record JobRequirements(
        String jobTitle,
        List<String> programmingLanguages,
        List<String> backendAndInfrastructureRequirements,
        List<String> agentRequirements,
        List<String> ragRequirements,
        List<String> engineeringRequirements,
        List<String> bonusQualifications,
        List<String> responsibilities,
        List<String> interviewTopics
) {
    public JobRequirements {
        programmingLanguages = List.copyOf(programmingLanguages);
        backendAndInfrastructureRequirements =
                List.copyOf(backendAndInfrastructureRequirements);
        agentRequirements = List.copyOf(agentRequirements);
        ragRequirements = List.copyOf(ragRequirements);
        engineeringRequirements = List.copyOf(engineeringRequirements);
        bonusQualifications = List.copyOf(bonusQualifications);
        responsibilities = List.copyOf(responsibilities);
        interviewTopics = List.copyOf(interviewTopics);
    }
}