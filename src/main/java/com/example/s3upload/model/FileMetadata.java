package com.example.s3upload.model;

import java.time.Instant;

public record FileMetadata(
        String fileId,
        String fileName,
        String contentType,
        long fileSize,
        String s3Key,
        Instant createdAt
) {
}

