package com.example.s3upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

public record UploadUrlRequest(
        @NotBlank String fileName,
        @NotBlank String contentType,
        @Positive
        @Max(value = 5_368_709_120L, message = "fileSize must not exceed 5 GiB")
        long fileSize
) {
}
