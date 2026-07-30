package com.leo.careerforgeai.system.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-07-28 16:44
 **/
@RestController
public class Health {

    @GetMapping("/health")
    public boolean ok() {
        return true;
    }
}