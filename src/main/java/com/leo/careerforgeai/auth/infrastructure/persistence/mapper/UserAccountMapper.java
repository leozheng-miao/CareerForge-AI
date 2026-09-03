package com.leo.careerforgeai.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.auth.infrastructure.persistence.entity.UserAccountEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * @program: CareerForge-AI
 * @description: 操作用户账户并执行乐观锁更新
 * @author: Miao Zheng
 * @date: 2026-09-02
 **/
@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccountEntity> {

    @Update("""
            UPDATE user_account
            SET display_name = #{account.displayName},
                password_hash = #{account.passwordHash},
                account_status = #{account.accountStatus},
                version = #{account.version},
                updated_at = #{account.updatedAt},
                last_login_at = #{account.lastLoginAt},
                disabled_at = #{account.disabledAt}
            WHERE user_id = #{account.userId}
              AND version = #{expectedVersion}
            """)
    int updateIfVersionMatches(
            @Param("account") UserAccountEntity account,
            @Param("expectedVersion") long expectedVersion
    );
}