package com.sangui.shop.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.common.web.SanguiAuthenticationContextFilter;
import com.sangui.shop.common.web.SanguiPrincipalArgumentResolver;
import com.sangui.shop.order.api.dto.ReviewImageUploadResponse;
import com.sangui.shop.order.application.ReviewImageStorageService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@WebMvcTest(
        controllers = ReviewImageUploadController.class,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.sentinel.enabled=false"
        }
)
@Import({GlobalApiExceptionHandler.class, ReviewImageUploadControllerTest.ResolverConfig.class})
class ReviewImageUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewImageStorageService reviewImageStorageService;

    @Test
    void uploadRequiresPrincipalAndReturnsPublicUrl() throws Exception {
        when(reviewImageStorageService.store(any()))
                .thenReturn(new ReviewImageUploadResponse(
                        "/api/uploads/review-images/review-a.jpg",
                        "image/jpeg",
                        12L
                ));
        SanguiPrincipal principal = new SanguiPrincipal("10001", 1L, java.util.Set.of("USER"), java.util.Set.of(), "jwt-1");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "review.jpg",
                "image/jpeg",
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}
        );

        mockMvc.perform(multipart("/api/uploads/review-images")
                        .file(file)
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-upload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REVIEW_IMAGE_UPLOADED"))
                .andExpect(jsonPath("$.traceId").value("trace-upload"))
                .andExpect(jsonPath("$.data.url").value("/api/uploads/review-images/review-a.jpg"))
                .andExpect(jsonPath("$.data.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.data.sizeBytes").value(12));

        ArgumentCaptor<org.springframework.web.multipart.MultipartFile> fileCaptor =
                ArgumentCaptor.forClass(org.springframework.web.multipart.MultipartFile.class);
        verify(reviewImageStorageService).store(fileCaptor.capture());
        assertThat(fileCaptor.getValue().getOriginalFilename()).isEqualTo("review.jpg");
    }

    @Test
    void uploadRejectsMissingPrincipal() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "review.jpg", "image/jpeg", new byte[] {1});

        mockMvc.perform(multipart("/api/uploads/review-images")
                        .file(file)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-upload-missing-principal"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
    }

    @TestConfiguration
    static class ResolverConfig {

        @Bean
        WebMvcConfigurer sanguiPrincipalResolverConfigurer() {
            return new WebMvcConfigurer() {
                @Override
                public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                    resolvers.add(new SanguiPrincipalArgumentResolver());
                }
            };
        }
    }
}
