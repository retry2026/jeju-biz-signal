package kr.jeju.agent.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import kr.jeju.agent.web.dto.ChatRequest;

class ApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void 요청_검증_오류는_400과_필드가_포함된_계약_응답을_반환한다() throws Exception {
        mockMvc.perform(post("/test/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "8f1c2a4e-1b7d-4c55-9a0e-2d3f4b5c6d7e",
                                  "userType": "STARTUP",
                                  "area": "50110660",
                                  "industry": "CAFE",
                                  "myStore": null,
                                  "message": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.isA(String.class)))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.field").value("message"));
    }

    @Test
    void 읽을_수_없는_요청_본문은_400_INVALID_REQUEST를_반환한다() throws Exception {
        mockMvc.perform(post("/test/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.isA(String.class)))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.field").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void 처리되지_않은_오류는_내부_정보를_노출하지_않고_500을_반환한다() throws Exception {
        mockMvc.perform(get("/test/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.field").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private details"))));
    }

    @RestController
    static class TestController {

        @PostMapping("/test/chat")
        String chat(@RequestBody @Valid ChatRequest request) {
            return "ok";
        }

        @GetMapping("/test/failure")
        String failure() {
            throw new IllegalStateException("private details");
        }
    }
}
