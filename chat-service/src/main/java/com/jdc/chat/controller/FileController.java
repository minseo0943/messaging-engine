package com.jdc.chat.controller;

import com.jdc.chat.domain.dto.PresignedUrlResponse;
import com.jdc.chat.service.FileUploadService;
import com.jdc.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@Tag(name = "File Upload", description = "파일 업로드/다운로드 API (MinIO Presigned URL)")
@RestController
@RequestMapping("/api/chat/files")
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class FileController {

    private final FileUploadService fileUploadService;

    @Operation(summary = "업로드 URL 발급",
            description = "클라이언트가 MinIO에 직접 업로드할 수 있는 Presigned PUT URL을 발급합니다")
    @PostMapping("/upload-url")
    public ApiResponse<PresignedUrlResponse> getUploadUrl(
            @RequestParam String fileName,
            @RequestParam(defaultValue = "application/octet-stream") String contentType) {
        if (fileName == null || fileName.isBlank() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("유효하지 않은 파일명입니다");
        }
        FileUploadService.PresignedUrlResult result = fileUploadService.generateUploadUrl(fileName, contentType);
        return ApiResponse.ok(new PresignedUrlResponse(result.uploadUrl(), result.objectKey()));
    }

    @Operation(summary = "다운로드 URL 발급",
            description = "MinIO에 저장된 파일의 Presigned GET URL을 발급합니다 (1시간 유효)")
    @GetMapping("/download-url")
    public ApiResponse<String> getDownloadUrl(@RequestParam String objectKey) {
        return ApiResponse.ok(fileUploadService.generateDownloadUrl(objectKey));
    }
}
