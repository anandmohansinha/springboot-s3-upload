package com.example.s3upload.service;

import com.example.s3upload.config.S3Properties;
import com.example.s3upload.dto.DownloadUrlResponse;
import com.example.s3upload.dto.UploadUrlRequest;
import com.example.s3upload.dto.UploadUrlResponse;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FileMetadataServiceTests {

    @Test
    void createsUploadAndDownloadUrlsWithoutCallingAws() {
        S3Properties properties = new S3Properties(
                "test-private-bucket",
                "us-east-1",
                Duration.ofMinutes(15),
                Duration.ofMinutes(5)
        );

        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")
                ))
                .build()) {
            FileMetadataService service = new FileMetadataService(presigner, properties);

            UploadUrlResponse upload = service.createUploadUrl(
                    new UploadUrlRequest("../resume.pdf", "application/pdf", 204_800)
            );
            DownloadUrlResponse download = service.createDownloadUrl(upload.fileId());

            assertThat(upload.fileId()).startsWith("file-");
            assertThat(upload.s3Key()).isEqualTo("uploads/" + upload.fileId() + "/resume.pdf");
            assertThat(upload.uploadUrl())
                    .contains("test-private-bucket")
                    .contains("X-Amz-Expires=900");
            assertThat(upload.expiresInSeconds()).isEqualTo(900);

            assertThat(download.fileId()).isEqualTo(upload.fileId());
            assertThat(download.downloadUrl())
                    .contains("test-private-bucket")
                    .contains("X-Amz-Expires=300");
            assertThat(download.expiresInSeconds()).isEqualTo(300);
        }
    }
}
