package com.sangui.shop.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.order.config.ReviewImageStorageProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ReviewImageStorageServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void storeAcceptsValidPngAndReturnsOnlyPublicUrl() throws Exception {
        ReviewImageStorageService service = new ReviewImageStorageService(properties(1024));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "review.png",
                "image/png",
                new byte[] {
                        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                        0x00, 0x00, 0x00, 0x0D
                }
        );

        var response = service.store(file);

        assertThat(response.url()).startsWith("/api/uploads/review-images/");
        assertThat(response.url()).endsWith(".png");
        assertThat(response.url()).doesNotContain(tempDir.toString());
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.sizeBytes()).isEqualTo(file.getSize());
        assertThat(Files.list(tempDir)).hasSize(1);
    }

    @Test
    void storeRejectsUnsupportedTypeTooLargeAndMismatchedMime() {
        ReviewImageStorageService service = new ReviewImageStorageService(properties(8));

        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                "file",
                "bad.txt",
                "text/plain",
                "not-image".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        ))).isInstanceOfSatisfying(SanguiException.class, exception -> assertThat(exception.errorCode().code()).isEqualTo("VALIDATION_FAILED"));

        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                "file",
                "large.jpg",
                "image/jpeg",
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5, 6}
        ))).isInstanceOfSatisfying(SanguiException.class, exception -> assertThat(exception.errorCode().code()).isEqualTo("VALIDATION_FAILED"));

        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                "file",
                "mismatch.png",
                "image/jpeg",
                new byte[] {
                        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                        0x00, 0x00, 0x00, 0x0D
                }
        ))).isInstanceOfSatisfying(SanguiException.class, exception -> assertThat(exception.errorCode().code()).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void loadRejectsUnsafeFileNames() {
        ReviewImageStorageService service = new ReviewImageStorageService(properties(1024));

        assertThatThrownBy(() -> service.load("../secret.jpg"))
                .isInstanceOfSatisfying(SanguiException.class, exception -> assertThat(exception.errorCode().code()).isEqualTo("VALIDATION_FAILED"));
    }

    private ReviewImageStorageProperties properties(long maxSize) {
        ReviewImageStorageProperties properties = new ReviewImageStorageProperties();
        properties.setStorageDirectory(tempDir.toString());
        properties.setPublicBasePath("/api/uploads/review-images/");
        properties.setMaxFileSizeBytes(maxSize);
        return properties;
    }
}
