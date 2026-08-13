package com.leo.careerforgeai.memory.application.context;


import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurnStatus;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurnRole;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 在用户隔离和预算约束下组装可发送给模型的结构化会话Context
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
public class ConversationContextAssembler {

    private final ConversationContextPolicy policy;

    public ConversationContextAssembler(ConversationContextPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy 不能为空");
    }

    public ConversationContext assemble(ConversationTurn currentUserTurn,
                                        List<ConversationTurn> recentTurns,
                                        List<MemoryItem> ownerMemories) {
        validateCurrentUserTurn(currentUserTurn);
        Objects.requireNonNull(recentTurns, "recentTurns 不能为空");
        Objects.requireNonNull(ownerMemories, "ownerMemories 不能为空");

        validateDataBoundary(currentUserTurn, recentTurns, ownerMemories);

        List<ConversationContext.ConversationExchange> completeExchanges =
                buildCompleteExchanges(currentUserTurn, recentTurns);

        List<MemoryItem> confirmedMemories = ownerMemories.stream()
                .filter(memory -> memory.status().isEffectiveProfileMemory())
                .sorted(Comparator.comparing(MemoryItem::updatedAt).reversed()
                        .thenComparing(memory -> memory.memoryId().toString()))
                .toList();

        int currentChars = currentUserTurn.content().length();
        ensureCurrentMessageFits(currentChars);

        boolean mayIncludeMemoryContext = policy.maxMemories() > 0 && !confirmedMemories.isEmpty();
        int reservedMemoryMessages = mayIncludeMemoryContext ? 1 : 0;
        int maxRoundsByMessages = Math.max(0, (policy.maxMessages() - 1 - reservedMemoryMessages) / 2);
        int maxHistoryRounds = Math.min(policy.maxRounds(), maxRoundsByMessages);

        Deque<ConversationContext.ConversationExchange> selectedExchanges = new ArrayDeque<>();
        int totalChars = currentChars;

        for (int index = completeExchanges.size() - 1;
             index >= 0 && selectedExchanges.size() < maxHistoryRounds;
             index--) {
            ConversationContext.ConversationExchange exchange = completeExchanges.get(index);
            int candidateChars = totalChars + exchange.contentChars();
            if (!fitsBudget(candidateChars)) {
                break;
            }
            selectedExchanges.addFirst(exchange);
            totalChars = candidateChars;
        }

        List<ConversationContext.ConfirmedMemoryFact> selectedMemories = new ArrayList<>();
        if (mayIncludeMemoryContext) {
            for (MemoryItem memory : confirmedMemories) {
                if (selectedMemories.size() >= policy.maxMemories()) {
                    break;
                }

                ConversationContext.ConfirmedMemoryFact memoryFact = new ConversationContext.ConfirmedMemoryFact(
                        memory.memoryId(),
                        memory.type(),
                        memory.normalizedKey(),
                        memory.content()
                );

                int candidateChars = totalChars + memoryFact.contentChars();
                int candidateMessageCount = selectedExchanges.size() * 2 + 2;
                if (candidateMessageCount > policy.maxMessages() || !fitsBudget(candidateChars)) {
                    break;
                }

                selectedMemories.add(memoryFact);
                totalChars = candidateChars;
            }
        }

        List<ConversationContext.ConversationExchange> selectedExchangeList = List.copyOf(selectedExchanges);
        int messageCount = selectedExchangeList.size() * 2 + 1 + (selectedMemories.isEmpty() ? 0 : 1);

        ConversationContext.ContextUsage usage = new ConversationContext.ContextUsage(
                selectedExchangeList.size(),
                messageCount,
                selectedMemories.size(),
                totalChars,
                policy.estimateTokens(totalChars),
                selectedExchangeList.size() < completeExchanges.size(),
                selectedMemories.size() < confirmedMemories.size()
        );

        return new ConversationContext(
                currentUserTurn.sessionId(),
                selectedExchangeList,
                List.copyOf(selectedMemories),
                currentUserTurn.content(),
                usage
        );
    }

    private void validateCurrentUserTurn(ConversationTurn currentUserTurn) {
        Objects.requireNonNull(currentUserTurn, "currentUserTurn 不能为空");
        if (currentUserTurn.role() != ConversationTurnRole.USER) {
            throw new IllegalArgumentException("当前消息必须是USER消息");
        }
        if (currentUserTurn.status() != ConversationTurnStatus.COMPLETED) {
            throw new IllegalArgumentException("当前USER消息必须是COMPLETED状态");
        }
    }

    private void validateDataBoundary(ConversationTurn currentUserTurn,
                                      List<ConversationTurn> recentTurns,
                                      List<MemoryItem> ownerMemories) {
        Set<UUID> turnIds = new HashSet<>();
        Set<Long> turnSequences = new HashSet<>();

        for (ConversationTurn turn : recentTurns) {
            Objects.requireNonNull(turn, "recentTurns 不能包含null");
            if (!turn.ownerId().equals(currentUserTurn.ownerId())) {
                throw new IllegalArgumentException("会话历史包含其他用户的数据");
            }
            if (!turn.sessionId().equals(currentUserTurn.sessionId())) {
                throw new IllegalArgumentException("会话历史包含其他会话的数据");
            }
            if (!turnIds.add(turn.turnId())) {
                throw new IllegalArgumentException("会话历史包含重复turnId");
            }
            if (!turnSequences.add(turn.turnSequence())) {
                throw new IllegalArgumentException("会话历史包含重复turnSequence");
            }
        }

        for (MemoryItem memory : ownerMemories) {
            Objects.requireNonNull(memory, "ownerMemories 不能包含null");
            if (!memory.ownerId().equals(currentUserTurn.ownerId())) {
                throw new IllegalArgumentException("Memory列表包含其他用户的数据");
            }
        }
    }

    private List<ConversationContext.ConversationExchange> buildCompleteExchanges(
            ConversationTurn currentUserTurn,
            List<ConversationTurn> recentTurns) {
        Map<UUID, ExchangeParts> exchangePartsMap = new LinkedHashMap<>();

        List<ConversationTurn> orderedTurns = recentTurns.stream()
                .filter(turn -> !turn.turnId().equals(currentUserTurn.turnId()))
                .sorted(Comparator.comparingLong(ConversationTurn::turnSequence))
                .toList();

        for (ConversationTurn turn : orderedTurns) {
            if (turn.status() != ConversationTurnStatus.COMPLETED) {
                continue;
            }

            ExchangeParts parts = exchangePartsMap.computeIfAbsent(
                    turn.exchangeId(),
                    ignored -> new ExchangeParts()
            );

            if (turn.role() == ConversationTurnRole.USER) {
                parts.setUserTurn(turn);
            } else if (turn.role() == ConversationTurnRole.ASSISTANT) {
                parts.setAssistantTurn(turn);
            }
        }

        return exchangePartsMap.values().stream()
                .filter(ExchangeParts::isComplete)
                .map(ExchangeParts::toConversationExchange)
                .sorted(Comparator.comparingLong(ConversationContext.ConversationExchange::userSequence))
                .toList();
    }

    private void ensureCurrentMessageFits(int currentChars) {
        if (!fitsBudget(currentChars)) {
            throw new IllegalArgumentException("当前消息超过Context预算");
        }
    }

    private boolean fitsBudget(int chars) {
        return chars <= policy.maxContentChars()
                && policy.estimateTokens(chars) <= policy.maxEstimatedTokens();
    }

    /**
     * @program: CareerForge-AI
     * @description: 临时收集同一轮对话中的USER消息和ASSISTANT消息
     * @author: Miao Zheng
     * @date: 2026-08-12
     **/
    private static final class ExchangeParts {

        private ConversationTurn userTurn;
        private ConversationTurn assistantTurn;

        private void setUserTurn(ConversationTurn turn) {
            if (userTurn != null) {
                throw new IllegalArgumentException("同一exchange存在多个USER消息");
            }
            userTurn = turn;
        }

        private void setAssistantTurn(ConversationTurn turn) {
            if (assistantTurn != null) {
                throw new IllegalArgumentException("同一exchange存在多个ASSISTANT消息");
            }
            assistantTurn = turn;
        }

        private boolean isComplete() {
            return userTurn != null && assistantTurn != null;
        }

        private ConversationContext.ConversationExchange toConversationExchange() {
            return new ConversationContext.ConversationExchange(
                    userTurn.exchangeId(),
                    userTurn.turnId(),
                    userTurn.turnSequence(),
                    userTurn.content(),
                    assistantTurn.turnId(),
                    assistantTurn.turnSequence(),
                    assistantTurn.content()
            );
        }
    }
}