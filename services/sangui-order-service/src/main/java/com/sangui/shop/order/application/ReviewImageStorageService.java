package com.sangui.shop.order.application;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.order.api.dto.ReviewImageUploadResponse;
import com.sangui.shop.order.config.ReviewImageStorageProperties;
import com.sangui.shop.order.domain.OrderErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReviewImageStorageService {

    private static final Pattern STORED_FILE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+\\.(jpg|jpeg|png|webp)$");

    private final Path storageDirectory;
    private final String publicBasePath;
    private final long maxFileSizeBytes;

    public ReviewImageStorageService(ReviewImageStorageProperties properties) {
        this.storageDirectory = Path.of(properties.getStorageDirectory()).toAbsolutePath().normalize();
        this.publicBasePath = normalizePublicBasePath(properties.getPublicBasePath());
        this.maxFileSizeBytes = properties.getMaxFileSizeBytes();
    }

    public ReviewImageUploadResponse store(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0 || file.getSize() > maxFileSizeBytes) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }

        byte[] header = readHeader(file);
        ImageType imageType = detectImageType(header)
                .orElseThrow(() -> new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400));
        validateContentType(file.getContentType(), imageType);

        String fileName = UUID.randomUUID() + "." + imageType.extension();
        Path target = storageDirectory.resolve(fileName).normalize();
        if (!target.startsWith(storageDirectory)) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }

        try {
            Files.createDirectories(storageDirectory);
            file.transferTo(target);
        } catch (IOException exception) {
            throw new SanguiException(CommonErrorCode.INTERNAL_ERROR, 500);
        }

        return new ReviewImageUploadResponse(
                publicBasePath + "/" + fileName,
                imageType.contentType(),
                file.getSize()
        );
    }

    public Resource load(String fileName) {
        String normalizedFileName = normalizeStoredFileName(fileName);
        Path target = storageDirectory.resolve(normalizedFileName).normalize();
        if (!target.startsWith(storageDirectory) || !Files.isRegularFile(target)) {
            throw new SanguiException(OrderErrorCode.ORDER_REVIEW_IMAGE_NOT_FOUND, 404);
        }
        try {
            return new UrlResource(target.toUri());
        } catch (IOException exception) {
            throw new SanguiException(OrderErrorCode.ORDER_REVIEW_IMAGE_NOT_FOUND, 404);
        }
    }

    public String contentType(String fileName) {
        String lower = normalizeStoredFileName(fileName).toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private String normalizeStoredFileName(String fileName) {
        if (fileName == null || !STORED_FILE_NAME_PATTERN.matcher(fileName).matches()) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        return fileName;
    }

    private byte[] readHeader(MultipartFile file) {
        byte[] header = new byte[12];
        try (InputStream inputStream = file.getInputStream()) {
            int read = inputStream.read(header);
            if (read < 0) {
                return new byte[0];
            }
            if (read == header.length) {
                return header;
            }
            byte[] actual = new byte[read];
            System.arraycopy(header, 0, actual, 0, read);
            return actual;
        } catch (IOException exception) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
    }

    private Optional<ImageType> detectImageType(byte[] header) {
        if (header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return Optional.of(ImageType.JPEG);
        }
        if (header.length >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A) {
            return Optional.of(ImageType.PNG);
        }
        if (header.length >= 12
                && header[0] == 0x52
                && header[1] == 0x49
                && header[2] == 0x46
                && header[3] == 0x46
                && header[8] == 0x57
                && header[9] == 0x45
                && header[10] == 0x42
                && header[11] == 0x50) {
            return Optional.of(ImageType.WEBP);
        }
        return Optional.empty();
    }

    private void validateContentType(String contentType, ImageType imageType) {
        if (contentType == null || contentType.isBlank()) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        if (!imageType.contentType().equals(contentType.toLowerCase(Locale.ROOT))) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
    }

    private String normalizePublicBasePath(String value) {
        if (value == null || value.isBlank()) {
            return "/api/uploads/review-images";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private enum ImageType {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        WEBP("webp", "image/webp");

        private final String extension;
        private final String contentType;

        ImageType(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        String extension() {
            return extension;
        }

        String contentType() {
            return contentType;
        }
    }
}
