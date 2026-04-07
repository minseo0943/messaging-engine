package com.jdc.chat.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true", matchIfMissing = false)
public class FileUploadService {

    private final MinioClient minioClient;
    private final String bucketName;

    public FileUploadService(MinioClient minioClient,
                             @Value("${minio.bucket:chat-files}") String bucketName) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
        initBucket();
    }

    private void initBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("MinIO 버킷 생성 완료 [bucket={}]", bucketName);
            }
        } catch (Exception e) {
            log.error("MinIO 버킷 초기화 실패: {}", e.getMessage());
        }
    }

    public PresignedUrlResult generateUploadUrl(String originalFileName, String contentType) {
        String objectKey = generateObjectKey(originalFileName);

        try {
            String uploadUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(10, TimeUnit.MINUTES)
                            .build());

            log.info("Presigned upload URL 생성 [objectKey={}]", objectKey);
            return new PresignedUrlResult(uploadUrl, objectKey);
        } catch (Exception e) {
            throw new RuntimeException("Presigned upload URL 생성 실패", e);
        }
    }

    public String generateDownloadUrl(String objectKey) {
        try {
            String downloadUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(1, TimeUnit.HOURS)
                            .build());

            log.info("Presigned download URL 생성 [objectKey={}]", objectKey);
            return downloadUrl;
        } catch (Exception e) {
            throw new RuntimeException("Presigned download URL 생성 실패", e);
        }
    }

    private String generateObjectKey(String originalFileName) {
        String extension = "";
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFileName.substring(dotIndex);
        }
        return UUID.randomUUID() + extension;
    }

    public record PresignedUrlResult(String uploadUrl, String objectKey) {}
}
