package com.sangui.shop.order.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.order.api.dto.ReviewImageUploadResponse;
import com.sangui.shop.order.application.ReviewImageStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads/review-images")
public class ReviewImageUploadController {

    private final ReviewImageStorageService reviewImageStorageService;

    public ReviewImageUploadController(ReviewImageStorageService reviewImageStorageService) {
        this.reviewImageStorageService = reviewImageStorageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<ReviewImageUploadResponse> upload(
            SanguiPrincipal principal,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request
    ) {
        ReviewImageUploadResponse response = reviewImageStorageService.store(file);
        return ApiResult.ok("REVIEW_IMAGE_UPLOADED", response, traceId(request));
    }

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> read(@PathVariable String fileName) {
        Resource resource = reviewImageStorageService.load(fileName);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(reviewImageStorageService.contentType(fileName)))
                .body(resource);
    }

    private String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }
}
