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

    @GetMapping("/download/{filename}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadFile(
            @org.springframework.web.bind.annotation.PathVariable String filename) {
        try {
            var data = fileStorageService.readFromInput(filename);
            var resource = new org.springframework.core.io.ByteArrayResource(data);

            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(data.length)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @org.springframework.web.bind.annotation.PostMapping("/upload")
    public ResponseEntity<ApiResponse<String>> uploadFile(
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("File is empty"));
            }
            String filename = file.getOriginalFilename();
            fileStorageService.writeToOutput(filename, file.getBytes());
            return ResponseEntity.ok(ApiResponse.success("File uploaded successfully", filename));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to upload file: " + e.getMessage()));
        }
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{filename}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @org.springframework.web.bind.annotation.PathVariable String filename) {
        try {
            fileStorageService.deleteFile(filename);
            return ResponseEntity.ok(ApiResponse.success("File deleted successfully", null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to delete file: " + e.getMessage()));
        }
    }

    @org.springframework.web.bind.annotation.PostMapping("/cleanup-temp")
    public ResponseEntity<ApiResponse<Void>> cleanupTemp() {
        try {
            fileStorageService.cleanTempDirectory();
            return ResponseEntity.ok(ApiResponse.success("Temp directory cleaned", null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to clean temp directory: " + e.getMessage()));
        }
    }
}
