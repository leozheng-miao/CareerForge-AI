package com.leo.careerforgeai.agent.application.run;

import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 根据规范化Session、版本和消息生成Coaching Run幂等请求指纹
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Component
public class CoachingRunRequestFingerprintService {

    public String fingerprint(
            UUID sessionId,
            long expectedSessionVersion,
            String message
    ) {
        Objects.requireNonNull(sessionId, "sessionId不能为空");
        if (expectedSessionVersion < 0) {
            throw new IllegalArgumentException(
                    "expectedSessionVersion不能小于0"
            );
        }

        String normalizedMessage =
                ConversationTurn.normalizeContent(message);
        String canonicalInput =
                "sessionId=" + sessionId
                        + "\nexpectedSessionVersion="
                        + expectedSessionVersion
                        + "\nmessageLength="
                        + normalizedMessage.length()
                        + "\nmessage="
                        + normalizedMessage;

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(
                            canonicalInput.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前JDK不支持SHA-256",
                    exception
            );
        }
    }
}