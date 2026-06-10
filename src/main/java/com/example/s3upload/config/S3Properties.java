package com.example.s3upload.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "aws.s3")
public record S3Properties(
        String bucket,
        String region,
        Duration uploadUrlExpiration,
        Duration downloadUrlExpiration
) {
}

