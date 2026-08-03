package com.leo.careerforgeai.knowledge.infrastructure.elasticsearch;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 配置知识索引 Alias 和版本号，并生成具体索引名称。
 * @author: Miao Zheng
 * @date: 2026-08-03 13:17
 **/
@Getter
@ConfigurationProperties(prefix = "careerforge.knowledge.elasticsearch", ignoreUnknownFields = false)
public class KnowledgeIndexProperties {
    private static final Pattern VALID_COMPONENT = Pattern.compile("[a-z0-9][a-z0-9._-]*");

    private final String indexAlias;
    private final String indexVersion;

    public KnowledgeIndexProperties(String indexAlias, String indexVersion) {
        validateComponent(indexAlias, "indexAlias");
        validateComponent(indexVersion, "indexVersion");
        this.indexAlias = indexAlias;
        this.indexVersion = indexVersion;
    }

    /** 根据 Alias 和版本生成本次构建使用的具体索引名称。 */
    public String concreteIndexName() {
        return indexAlias + "-" + indexVersion;
    }

    private void validateComponent(String value, String fieldName) {
        if (value == null || !VALID_COMPONENT.matcher(value).matches()) throw new IllegalArgumentException(fieldName + " 必须由小写字母、数字、点、下划线或连字符组成");
    }
}