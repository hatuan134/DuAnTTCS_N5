package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.BookCoverController;
import com.duanttcsn5.library.entity.BookCoverImage;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.security.JwtAuthenticationFilter;
import com.duanttcsn5.library.security.RestAccessDeniedHandler;
import com.duanttcsn5.library.security.RestAuthenticationEntryPoint;
import com.duanttcsn5.library.service.BookCoverService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BookCoverControllerTest {
    @Configuration @EnableWebMvc
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    @Import({SecurityConfig.class, BookCoverController.class, GlobalExceptionHandler.class,
            RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean BookCoverService covers() { return mock(BookCoverService.class); }
        @Bean JwtAuthenticationFilter jwt() throws Exception {
            var filter = mock(JwtAuthenticationFilter.class);
            doAnswer(invocation -> {
                ((jakarta.servlet.FilterChain) invocation.getArgument(2)).doFilter(
                        invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(filter).doFilter(any(), any(), any());
            return filter;
        }
    }
    private AnnotationConfigWebApplicationContext context;
    private BookCoverService service;
    private MockMvc mvc;
    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        service = context.getBean(BookCoverService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }
    private MockMultipartFile file() { return new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1}); }

    @Test void anonymousUploadIs401() throws Exception {
        mvc.perform(multipart("/api/v1/books/7/cover").file(file())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void readerUploadIs403() throws Exception {
        mvc.perform(multipart("/api/v1/books/7/cover").file(file()).with(user("reader").roles("READER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void staffUploadIsAllowed() throws Exception {
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            mvc.perform(multipart("/api/v1/books/7/cover").file(file()).with(user("staff").roles(role)))
                    .andExpect(status().isNoContent());
        }
        verify(service, times(3)).upload(eq(7L), any());
    }
    @Test void readerCannotReadUnpublishedStaffCover() throws Exception {
        mvc.perform(get("/api/v1/books/7/cover").with(user("reader").roles("READER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void publicImageHasCorrectTypeAndBytes() throws Exception {
        when(service.get(7L, true)).thenReturn(new BookCoverImage(7L, "image/png", new byte[]{1, 2}));
        mvc.perform(get("/api/v1/books/public/7/cover")).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andExpect(content().bytes(new byte[]{1, 2}))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
    @Test void multipartLimitErrorUsesVietnameseResponse() throws Exception {
        doThrow(new org.springframework.web.multipart.MaxUploadSizeExceededException(BookCoverService.MAX_BYTES))
                .when(service).upload(eq(7L), any());
        mvc.perform(multipart("/api/v1/books/7/cover").file(file()).with(user("staff").roles("LIBRARIAN")))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("code").value("INVALID_BOOK_COVER"))
                .andExpect(jsonPath("message").value(BookCoverService.INVALID_MESSAGE));
    }

    @Test void anonymousCanReadPublicThumbnail() throws Exception {
        when(service.getThumbnail(7L, true)).thenReturn(new BookCoverImage(7L, "image/png", new byte[]{3, 4}));
        mvc.perform(get("/api/v1/books/public/7/cover/thumbnail"))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[]{3, 4}))
                .andExpect(header().string("Content-Length", "2"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        verify(service).getThumbnail(7L, true);
        verify(service, never()).get(anyLong(), anyBoolean());
    }

    @Test void versionedOriginalAndThumbnailAreNotCached() throws Exception {
        when(service.get(7L, true)).thenReturn(new BookCoverImage(7L, "image/jpeg", new byte[]{8, 9}));
        when(service.getThumbnail(7L, true)).thenReturn(new BookCoverImage(7L, "image/png", new byte[]{5, 6}));
        mvc.perform(get("/api/v1/books/public/7/cover").param("v", "new-version"))
                .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(new byte[]{8, 9}))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/books/public/7/cover/thumbnail").param("v", "new-version"))
                .andExpect(status().isOk()).andExpect(content().bytes(new byte[]{5, 6}))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
