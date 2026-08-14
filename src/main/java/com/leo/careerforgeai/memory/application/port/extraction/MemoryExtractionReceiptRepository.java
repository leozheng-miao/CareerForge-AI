package com.leo.careerforgeai.memory.application.port.extraction;

import com.leo.careerforgeai.memory.domain.extraction.MemoryExtractionReceipt;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.Optional;

/**
 * @program: CareerForge-AI
 * @description: 定义成功Memory提取凭证的owner隔离持久化端口
 * @author: Miao Zheng
 * @date: 2026-08-14
 **/
public interface MemoryExtractionReceiptRepository {

    /** 保存一次成功提取凭证，唯一键冲突由上层执行幂等回放。 */
    void insert(MemoryExtractionReceipt receipt);

    /** 按owner和稳定输入身份查询成功凭证。 */
    Optional<MemoryExtractionReceipt> findByIdentity(
            ActorId ownerId,
            String extractorVersion,
            String inputFingerprint
    );
}