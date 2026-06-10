package com.example.s3upload.controller;

import com.example.s3upload.dto.DownloadUrlResponse;
import com.example.s3upload.dto.UploadUrlRequest;
import com.example.s3upload.dto.UploadUrlResponse;
import com.example.s3upload.service.FileMetadataService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileMetadataService fileMetadataService;

    public FileController(FileMetadataService fileMetadataService) {
        this.fileMetadataService = fileMetadataService;
    }

    @PostMapping("/upload-url")
    public ResponseEntity<UploadUrlResponse> createUploadUrl(
            @Valid @RequestBody UploadUrlRequest request
    ) {
        return ResponseEntity.ok(fileMetadataService.createUploadUrl(request));
    }

    @GetMapping("/{fileId}/download-url")
    public ResponseEntity<DownloadUrlResponse> createDownloadUrl(
            @PathVariable String fileId
    ) {
        return ResponseEntity.ok(fileMetadataService.createDownloadUrl(fileId));
    }
}

