package com.leo.careerforgeai.interviewflow.application.tool;

import com.leo.careerforgeai.agent.domain.tool.ToolExecutionContext;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort.KnowledgeQuestionCandidate;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort.SourceReference;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchInterviewQuestionsToolTest {

    @Test
    void shouldRemainReadOnlyAndRequireServerInjectedScope() {
        SourceReference source = new SourceReference(
                "kb", "doc", "a".repeat(64)
        );
        SearchInterviewQuestionsTool tool = new SearchInterviewQuestionsTool(
                (direction, objective, limit) -> List.of(
                        new KnowledgeQuestionCandidate(
                                "受控公共题库片段",
                                List.of(source)
                        )
                )
        );
        RetrievalScope scope = new RetrievalScope(
                "kb",
                Set.of(KnowledgeDocumentType.INTERVIEW_EXPERIENCE),
                Set.of()
        );

        assertThat(tool.contract().name())
                .isEqualTo("search_interview_questions");
        assertThat(tool.contract().readOnly()).isTrue();
        assertThatThrownBy(() -> tool.execute(
                new SearchInterviewQuestionsTool.Input("AI Agent", "RAG", 3),
                new ToolExecutionContext(
                        "run", Instant.now().plusSeconds(10), scope)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner");

        var output = tool.execute(
                new SearchInterviewQuestionsTool.Input("AI Agent", "RAG", 3),
                new ToolExecutionContext(
                        "run",
                        Instant.now().plusSeconds(10),
                        scope,
                        new ActorId("owner"),
                        UUID.randomUUID()
                )
        );
        assertThat(output.data().candidates()).hasSize(1);
        assertThat(output.data().candidates().getFirst().sourceReferences())
                .containsExactly(source);
    }
}
