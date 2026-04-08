package com.jdc.chat.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FileUploadServiceTest {

    @Mock
    private MinioClient minioClient;

    @Test
    @DisplayName("Presigned upload URL이 생성되고 objectKey에 확장자가 포함되는 테스트")
    void generateUploadUrl_shouldReturnUrlWithExtension() throws Exception {
        // Given
        given(minioClient.bucketExists(any())).willReturn(true);
        given(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .willReturn("https://minio.local/chat-files/uuid-abc.jpg");

        FileUploadService service = new FileUploadService(minioClient, "chat-files");

        // When
        FileUploadService.PresignedUrlResult result = service.generateUploadUrl("photo.jpg", "image/jpeg");

        // Then
        assertThat(result.uploadUrl()).contains("minio.local");
        assertThat(result.objectKey()).endsWith(".jpg");
    }

    @Test
    @DisplayName("Presigned download URL이 정상 생성되는 테스트")
    void generateDownloadUrl_shouldReturnUrl() throws Exception {
        // Given
        given(minioClient.bucketExists(any())).willReturn(true);
        given(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .willReturn("https://minio.local/chat-files/abc.pdf");

        FileUploadService service = new FileUploadService(minioClient, "chat-files");

        // When
        String url = service.generateDownloadUrl("abc.pdf");

        // Then
        assertThat(url).isEqualTo("https://minio.local/chat-files/abc.pdf");
    }

    @Test
    @DisplayName("MinIO 오류 시 RuntimeException이 발생하는 테스트")
    void generateUploadUrl_shouldThrow_whenMinioFails() throws Exception {
        // Given
        given(minioClient.bucketExists(any())).willReturn(true);
        given(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .willThrow(new RuntimeException("MinIO connection refused"));

        FileUploadService service = new FileUploadService(minioClient, "chat-files");

        // When & Then
        assertThatThrownBy(() -> service.generateUploadUrl("file.txt", "text/plain"))
                .isInstanceOf(RuntimeException.class);
    }
}
