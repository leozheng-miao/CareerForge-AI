package com.leo.careerforgeai.knowledge.infrastructure.document.loading;

/**
 * @program: CareerForge-AI
 * @description: 标识 Markdown 文档加载阶段的确定性失败类型
 * @author: Miao Zheng
 * @date: 2026-07-31 15:21
 **/
public enum DocumentLoadErrorType {
    ROOT_NOT_FOUND,
    ROOT_NOT_DIRECTORY,
    INVALID_PATH,
    PATH_OUTSIDE_ROOT,
    UNSUPPORTED_FILE_TYPE,
    FILE_NOT_FOUND,
    FILE_NOT_READABLE,
    INVALID_UTF8,
    EMPTY_DOCUMENT,
    READ_FAILED
}