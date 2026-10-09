package com.leo.careerforgeai.interviewflow.application.tool;

import com.leo.careerforgeai.agent.domain.tool.ToolContract;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** 面试决策Agent的最小工具白名单。 */
@Component
public final class InterviewToolCatalog {

    private final SearchInterviewQuestionsTool searchTool;

    public InterviewToolCatalog(SearchInterviewQuestionsTool searchTool) {
        this.searchTool = Objects.requireNonNull(
                searchTool,
                "searchTool不能为空"
        );
    }

    public List<ToolContract<?, ?>> contracts() {
        return List.of(searchTool.contract());
    }

    public SearchInterviewQuestionsTool searchTool() {
        return searchTool;
    }
}
