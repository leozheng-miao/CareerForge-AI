package com.leo.careerforgeai.model.api;

import com.leo.careerforgeai.model.domain.routing.ModelTaskType;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import com.leo.careerforgeai.model.application.ModelGateway;
import com.leo.careerforgeai.model.domain.ModelMessage;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.domain.ModelRole;
import com.leo.careerforgeai.model.domain.stream.ModelStreamEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-07-28 18:07
 **/
@RestController
@RequestMapping("api/model")
public class ModelTestController {

    private final ModelGateway modelGateway;
    private final TaskExecutor taskExecutor;
    /** 注入模型调用网关和专用流式任务执行器。 */
    public ModelTestController(
            ModelGateway modelGateway,
            @Qualifier("modelStreamTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.modelGateway = modelGateway;
        this.taskExecutor = taskExecutor;
    }

    @PostMapping("/chat")
    public BaseResponse<String> chat(@Valid @RequestBody ChatRequest request) {
        ModelRequest modelRequest = new ModelRequest(
                List.of(
                        new ModelMessage(ModelRole.SYSTEM, request.systemPrompt()),
                        new ModelMessage(ModelRole.USER, request.userPrompt())
                ),
                ModelOutputFormat.TEXT
        );
        ModelResponse res = modelGateway.chat(ModelTaskType.CAREER_COACH, modelRequest);

        return ResultUtils.success(res.content());
    }

    @PostMapping(
            value = "/chat/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        ModelRequest modelRequest = new ModelRequest(
                List.of(
                        new ModelMessage(ModelRole.SYSTEM, request.systemPrompt()),
                        new ModelMessage(ModelRole.USER, request.userPrompt())
                ),
                ModelOutputFormat.TEXT
        );
        taskExecutor.execute(() -> {
            try {
                modelGateway.stream(ModelTaskType.CAREER_COACH, modelRequest, event -> sendEvent(emitter, event));
                emitter.complete();
            }
            catch (RuntimeException e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void sendEvent(SseEmitter emitter, ModelStreamEvent event) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .name(event.type().name())
                            .data(event)
            );
        } catch (IOException e) {
            throw new UncheckedIOException("SSE事件发送失败", e);
        }
    }


    public record ChatRequest(
            @NotBlank String systemPrompt,
            @NotBlank String userPrompt
    ) {
    }
}