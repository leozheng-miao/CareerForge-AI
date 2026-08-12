package com.leo.careerforgeai.memory.experiment;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
    void shouldLoseMessagesWhenInMemoryRepositoryIsRecreated() {
        MessageWindowChatMemory beforeRestart = MessageWindowChatMemory.builder()
                .maxMessages(5)
                .build();

        beforeRestart.add(
                CONVERSATION_ID,
                new UserMessage("我每周可以学习十小时")
        );

        MessageWindowChatMemory afterRestart = MessageWindowChatMemory.builder()
                .maxMessages(5)
                .build();

        assertThat(beforeRestart.get(CONVERSATION_ID)).hasSize(1);
        assertThat(afterRestart.get(CONVERSATION_ID)).isEmpty();
    }
}