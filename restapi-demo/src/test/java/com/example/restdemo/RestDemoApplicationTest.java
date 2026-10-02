package com.example.restdemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RestDemoApplicationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void envComesFromSelectedResourcesFolder() throws Exception {
        mvc.perform(get("/api/env"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.env").isNotEmpty());
    }

    @Test
    void productCrud() throws Exception {
        mvc.perform(get("/api/products").param("keyword", "sữa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/products/000"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123\",\"name\":\"Bánh mì\",\"price\":2500}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123\",\"name\":\"Bánh mì\",\"price\":2500}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"thiếu code\"}"))
                .andExpect(status().isBadRequest());
    }
}
