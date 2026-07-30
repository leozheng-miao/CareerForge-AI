package com.leo.careerforgeai.common;

import lombok.Data;

import java.io.Serializable;

/**
 * @program: yu-picture
 * @description: 通用的删除请求类
 * @author: Miao Zheng
 * @date: 2025-10-21 13:51
 **/
@Data
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}