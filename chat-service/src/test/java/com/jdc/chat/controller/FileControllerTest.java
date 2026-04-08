package com.jdc.chat.controller;

import com.jdc.chat.service.FileUploadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private FileUploadService fileUploadService;

    @InjectMocks
    private FileController fileController;

    @Test
    @DisplayName("업로드 URL 요청 시 presigned URL과 objectKey를 반환하는 테스트")
    void getUploadUrl_shouldReturnPresignedUrl() {
        // Given
        FileUploadService.PresignedUrlResult result =
                new FileUploadService.PresignedUrlResult("https://minio/upload", "abc-123.jpg");
        given(fileUploadService.generateUploadUrl("photo.jpg", "image/jpeg"))
                .willReturn(result);

        // When
        var response = fileController.getUploadUrl("photo.jpg", "image/jpeg");

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().uploadUrl()).isEqualTo("https://minio/upload");
        assertThat(response.getData().objectKey()).isEqualTo("abc-123.jpg");
    }

    @Test
    @DisplayName("다운로드 URL 요청 시 presigned URL을 반환하는 테스트")
    void getDownloadUrl_shouldReturnPresignedUrl() {
        // Given
        given(fileUploadService.generateDownloadUrl("abc-123.jpg"))
                .willReturn("https://minio/download");

        // When
        var response = fileController.getDownloadUrl("abc-123.jpg");

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("https://minio/download");
    }
}
