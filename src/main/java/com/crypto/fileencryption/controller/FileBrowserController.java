package com.crypto.fileencryption.controller;

import com.crypto.fileencryption.model.ApiResponse;
import com.crypto.fileencryption.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileBrowserController {

    private final FileStorageService fileStorageService;

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<String>>> listInputFiles() {
        try {
            var files = fileStorageService.listInputFiles();
            return ResponseEntity.ok(ApiResponse.success(files));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to list files: " + e.getMessage()));
        }
    }
}
