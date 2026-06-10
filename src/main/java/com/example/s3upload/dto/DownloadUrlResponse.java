package com.example.s3upload.dto;

public record DownloadUrlResponse(
        String fileId,
        String downloadUrl,
        long expiresInSeconds
) {
}

