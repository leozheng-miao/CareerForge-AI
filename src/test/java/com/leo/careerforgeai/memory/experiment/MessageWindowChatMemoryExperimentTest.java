package com.leo.careerforgeai.memory.experiment;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @program: CareerForge-AI
 * @description: 对照Spring AI消息窗口、Advisor自动注入和自动保存行为
 * @author: Miao Zheng
 * @date: 2026-08-13
 **/
class MessageWindowChatMemoryExperimentTest {

    private static final String CONVERSATION_ID = "actor-a:session-001";

    @Test
    void shouldRetainRecentCompleteTurnsWithinMessageLimit() {
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(5)
                .build();

        memory.add(CONVERSATION_ID, List.of(
                new SystemMessage("服务端系统规则"),
                new UserMessage("第一轮问题"),
                new AssistantMessage("第一轮回答"),
                new UserMessage("第二轮问题"),
                new AssistantMessage("第二轮回答"),
                new UserMessage("第三轮问题"),
                new AssistantMessage("第三轮回答")
        ));

        List<Message> retainedMessages = memory.get(CONVERSATION_ID);

        assertThat(retainedMessages)
                .extracting(Message::getMessageType)
                .containsExactly(
                        MessageType.SYSTEM,
                        MessageType.USER,
                        MessageType.ASSISTANT,
                        MessageType.USER,
                        MessageType.ASSISTANT
                );

        assertThat(retainedMessages)
                .extracting(Message::getText)
                .containsExactly(
                        "服务端系统规则",
                        "第二轮问题",
                        "第二轮回答",
                        "第三轮问题",
                        "第三轮回答"
                );
    }

    @Test
    void shouldInjectHistoryAndStoreCurrentUserMessageThroughAdvisor() {
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(5)
                .build();

        memory.add(CONVERSATION_ID, List.of(
                new UserMessage("历史问题"),
                new AssistantMessage("历史回答")
        ));

        MessageChatMemoryAdvisor advisor =
                MessageChatMemoryAdvisor.builder(memory).build();

        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(List.of(
                        new SystemMessage("本轮服务端系统规则"),
                        new UserMessage("当前问题")
                )))
                .context(ChatMemory.CONVERSATION_ID, CONVERSATION_ID)
                .build();

        ChatClientRequest processedRequest =
                advisor.before(request, mock(AdvisorChain.class));

        assertThat(processedRequest.prompt().getInstructions())
                .extracting(Message::getMessageType)
                .containsExactly(
                        MessageType.SYSTEM,
                        MessageType.USER,
                        MessageType.ASSISTANT,
                        MessageType.USER
                );

        assertThat(processedRequest.prompt().getInstructions())
                .extracting(Message::getText)
                .containsExactly(
                        "本轮服务端系统规则",
                        "历史问题",
                        "历史回答",
                        "当前问题"
                );

        assertThat(memory.get(CONVERSATION_ID))
                .extracting(Message::getText)
                .containsExactly(
                        "历史问题",
                        "历史回答",
                        "当前问题"
                );
    }

    @Test
    void shouldAutoStoreAssistantOutputWithoutBusinessValidation() {
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(5)
                .build();

        MessageChatMemoryAdvisor advisor =
                MessageChatMemoryAdvisor.builder(memory).build();

        ChatClientResponse response = ChatClientResponse.builder()
                .chatResponse(new ChatResponse(List.of(
                        new Generation(
                                new AssistantMessage(
                                        "这不是合法的Career Coach JSON"
                                )
                        )
                )))
                .context(ChatMemory.CONVERSATION_ID, CONVERSATION_ID)
                .build();

        advisor.after(response, mock(AdvisorChain.class));

        assertThat(memory.get(CONVERSATION_ID))
                .extracting(Message::getMessageType)
                .containsExactly(MessageType.ASSISTANT);

        assertThat(memory.get(CONVERSATION_ID))
                .extracting(Message::getText)
                .containsExactly("这不是合法的Career Coach JSON");
    }

    @Test
    void shouldLoseMessagesWhenInMemoryRepositoryIsRecreated() {
        MessageWindowChatMemory beforeRestart =
                MessageWindowChatMemory.builder()
                        .maxMessages(5)
                        .build();

        beforeRestart.add(
                CONVERSATION_ID,
                new UserMessage("我每周可以学习十小时")
        );

        MessageWindowChatMemory afterRestart =
                MessageWindowChatMemory.builder()
                        .maxMessages(5)
                        .build();

        assertThat(beforeRestart.get(CONVERSATION_ID)).hasSize(1);
        assertThat(afterRestart.get(CONVERSATION_ID)).isEmpty();
    }
}