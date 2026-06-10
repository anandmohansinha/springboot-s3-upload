package com.example.s3upload.dto;

public record UploadUrlResponse(
        String fileId,
        String uploadUrl,
        String s3Key,
        long expiresInSeconds
) {
}

