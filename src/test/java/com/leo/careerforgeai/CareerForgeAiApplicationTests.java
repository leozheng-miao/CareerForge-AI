package com.leo.careerforgeai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"careerforge.model.base-url=http://localhost", "careerforge.model.api-key=test-placeholder", "careerforge.model.name=test-model"})
class CareerForgeAiApplicationTests {

    @Test
    void contextLoads() {
    }

}