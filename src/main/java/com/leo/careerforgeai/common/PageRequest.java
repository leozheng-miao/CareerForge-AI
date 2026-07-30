package com.leo.careerforgeai.common;

import lombok.Data;

/**
 * @program: yu-picture
 * @description:
 * @author: Miao Zheng
 * @date: 2025-10-21 13:50
 **/
@Data
public class PageRequest {

    /**
     * 当前页号
     */
    private int pageNum = 1;

    /**
     * 页面大小
     */
    private int pageSize = 10;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序顺序（默认升序）
     */
    private String sortOrder = "descend";
}