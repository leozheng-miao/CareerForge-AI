package com.leo.careerforgeai.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.auth.infrastructure.persistence.entity.RefreshTokenEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 操作Refresh Token并执行轮换、退出和重放家族撤销
 * @author: Miao Zheng
 * @date: 2026-09-02
 **/
@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshTokenEntity> {

    @Update("""
            UPDATE auth_refresh_token
            SET token_status = #{token.tokenStatus},
                rotated_at = #{token.rotatedAt},
                version = #{token.version}
            WHERE refresh_token_id = #{token.refreshTokenId}
              AND user_id = #{token.userId}
              AND family_id = #{token.familyId}
              AND token_status = 'ACTIVE'
              AND version = #{expectedVersion}
            """)
    int rotateIfActive(
            @Param("token") RefreshTokenEntity token,
            @Param("expectedVersion") long expectedVersion
    );

    @Update("""
            UPDATE auth_refresh_token
            SET token_status = 'REVOKED',
                revoked_at = #{revokedAt},
                version = version + 1
            WHERE user_id = #{userId}
              AND token_hash = #{tokenHash}
              AND token_status = 'ACTIVE'
            """)
    int revokeActiveToken(
            @Param("userId") String userId,
            @Param("tokenHash") String tokenHash,
            @Param("revokedAt") Instant revokedAt
    );

    @Update("""
            UPDATE auth_refresh_token
            SET token_status = 'REVOKED',
                revoked_at = #{revokedAt},
                version = version + 1
            WHERE user_id = #{userId}
              AND family_id = #{familyId}
              AND token_status = 'ACTIVE'
            """)
    int revokeActiveFamily(
            @Param("userId") String userId,
            @Param("familyId") String familyId,
            @Param("revokedAt") Instant revokedAt
    );
}