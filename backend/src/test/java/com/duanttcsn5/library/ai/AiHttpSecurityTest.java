package com.duanttcsn5.library.ai;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiHttpSecurityTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,AiController.class,AiAccess.class,GlobalExceptionHandler.class,JwtAuthenticationFilter.class,RestAuthenticationEntryPoint.class,RestAccessDeniedHandler.class})
    static class Config {
        // @EnableWebMvc runs a minimal MVC context, not Spring Boot auto-configuration.
        // AiController requires the Jackson 3 mapper provided automatically in production.
        @Bean tools.jackson.databind.ObjectMapper jsonMapper() {
            return tools.jackson.databind.json.JsonMapper.builder().build();
        }
        @Bean AiChatService chat() { return mock(AiChatService.class); }
        @Bean JwtService jwt() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;AiChatService chat;
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();
        chat=context.getBean(AiChatService.class);
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(new AiRequestSizeFilter()).apply(springSecurity()).build();
        when(chat.chat(any(),any())).thenReturn(new AiTypes.ChatResponse("00000000-0000-0000-0000-000000000000","Chào bạn",List.of(),"GROQ",false,19));
    }
    @AfterEach void close() { context.close(); }
    @Test void guestCanChatWithoutOpeningOldProtectedEndpoints() throws Exception {
        mvc.perform(post("/api/v1/ai/chat").contentType("application/json").content("{\"message\":\"Xin chào\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/v1/loans/me/borrowed-books")).andExpect(status().isUnauthorized());
    }
    @Test void invalidBearerNeverDowngradesToGuest() throws Exception {
        when(context.getBean(JwtService.class).decode("bad-token")).thenThrow(new org.springframework.security.oauth2.jwt.JwtException("bad"));
        mvc.perform(post("/api/v1/ai/chat").header("Authorization","Bearer bad-token").contentType("application/json").content("{\"message\":\"Hi\"}"))
            .andExpect(status().isUnauthorized());verifyNoInteractions(chat);
    }
    @Test void guestCannotReadOrDeleteAuthenticatedChatHistory() throws Exception {
        mvc.perform(get("/api/v1/ai/history")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ai/history/00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/ai/history/00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(chat);
    }
    @Test void oversizedAndBlankRequestsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/ai/chat").contentType("application/json").content("{\"message\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/ai/chat").contentType("application/json").content("{\"message\":\""+"a".repeat(1600)+"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/ai/chat").contentType("application/json").content("a".repeat(9000))).andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/api/v1/ai/chat/stream").contentType("application/json").content("a".repeat(9000))).andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(chat);
    }
    @Test void streamingReplaysFilteredBodyAndReturnsDoneEvent() throws Exception {
        when(chat.chat(any(),any(),any())).thenReturn(new AiTypes.ChatResponse(
            "00000000-0000-0000-0000-000000000000",AiScopePolicy.REFUSAL,List.of(),"LIBRA_SCOPE",false,Integer.MAX_VALUE));
        var pending=mvc.perform(post("/api/v1/ai/chat/stream").contentType("application/json")
            .content("{\"message\":\"Tết ngày nào?\"}"))
            .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("event: done")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("LIBRA_SCOPE")));
        verify(chat).chat(argThat(body->body.message().equals("Tết ngày nào?")),anyString(),any());
    }
    @Test void errorsNeverReturnProviderSecrets() throws Exception {
        when(chat.chat(any(),any())).thenThrow(new com.duanttcsn5.library.exception.ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"AI_PROVIDER_UNAVAILABLE","Dịch vụ AI tạm thời chưa sẵn sàng."));
        mvc.perform(post("/api/v1/ai/chat").contentType("application/json").content("{\"message\":\"hi\"}"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AI_PROVIDER_UNAVAILABLE"));
    }
}
