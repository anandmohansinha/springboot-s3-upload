package com.example.s3upload.service;

import com.example.s3upload.config.S3Properties;
import com.example.s3upload.dto.DownloadUrlResponse;
import com.example.s3upload.dto.UploadUrlRequest;
import com.example.s3upload.dto.UploadUrlResponse;
import com.example.s3upload.model.FileMetadata;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileMetadataService {

    private final Map<String, FileMetadata> metadataByFileId = new ConcurrentHashMap<>();
    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    public FileMetadataService(S3Presigner s3Presigner, S3Properties properties) {
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    public UploadUrlResponse createUploadUrl(UploadUrlRequest request) {
        String fileId = "file-" + UUID.randomUUID();
        String fileName = sanitizeFileName(request.fileName());
        String s3Key = "uploads/%s/%s".formatted(fileId, fileName);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(s3Key)
                .contentType(request.contentType())
                .contentLength(request.fileSize())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(properties.uploadUrlExpiration())
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        FileMetadata metadata = new FileMetadata(
                fileId,
                fileName,
                request.contentType(),
                request.fileSize(),
                s3Key,
                Instant.now()
        );
        metadataByFileId.put(fileId, metadata);

        return new UploadUrlResponse(
                fileId,
                presignedRequest.url().toString(),
                s3Key,
                properties.uploadUrlExpiration().toSeconds()
        );
    }

    public DownloadUrlResponse createDownloadUrl(String fileId) {
        FileMetadata metadata = metadataByFileId.get(fileId);
        if (metadata == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File metadata not found");
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(metadata.s3Key())
                .responseContentType(metadata.contentType())
                .responseContentDisposition("attachment; filename=\"" + metadata.fileName() + "\"")
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(properties.downloadUrlExpiration())
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

        return new DownloadUrlResponse(
                fileId,
                presignedRequest.url().toString(),
                properties.downloadUrlExpiration().toSeconds()
        );
    }

    private String sanitizeFileName(String fileName) {
        String normalized = fileName.replace('\\', '/');
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replace("\"", "")
                .replace("\r", "")
                .replace("\n", "");

        if (baseName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file name");
        }
        return baseName;
    }
}

