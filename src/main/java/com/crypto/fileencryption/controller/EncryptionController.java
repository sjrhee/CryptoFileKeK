package com.crypto.fileencryption.controller;

import com.crypto.fileencryption.model.ApiResponse;
import com.crypto.fileencryption.model.EncryptionResult;
import com.crypto.fileencryption.service.DekService;
import com.crypto.fileencryption.service.FileEncryptionService;
import com.crypto.fileencryption.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.SecretKey;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for file encryption operations
 */
@Slf4j
@RestController
@RequestMapping("/api/encrypt")
@RequiredArgsConstructor
public class EncryptionController {

    private final DekService dekService;
    private final FileEncryptionService fileEncryptionService;
    private final FileStorageService fileStorageService;

    // Temporary storage for encryption session data
    private final Map<String, EncryptionSession> sessions = new HashMap<>();

    /**
     * Upload file for encryption
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadFile(
            @RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Please select a file to upload"));
            }

            log.info("Received file for encryption: {} ({} bytes)",
                    file.getOriginalFilename(), file.getSize());

            // Store original file
            byte[] fileData = file.getBytes();
            String fileId = fileStorageService.storeFile(fileData, file.getOriginalFilename());

            // Create session
            EncryptionSession session = new EncryptionSession();
            session.fileId = fileId;
            session.originalFilename = file.getOriginalFilename();
            session.originalSize = fileData.length;
            sessions.put(fileId, session);

            Map<String, Object> response = new HashMap<>();
            response.put("fileId", fileId);
            response.put("filename", file.getOriginalFilename());
            response.put("size", file.getSize());

            return ResponseEntity.ok(ApiResponse.success("File uploaded successfully", response));

        } catch (Exception e) {
            log.error("Error uploading file", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to upload file: " + e.getMessage()));
        }
    }

    /**
     * Process encryption
     */
    @PostMapping("/process/{fileId}")
    public ResponseEntity<ApiResponse<EncryptionResult>> processEncryption(
            @PathVariable String fileId) {
        try {
            EncryptionSession session = sessions.get(fileId);
            if (session == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Invalid file ID or session expired"));
            }

            log.info("Processing encryption for file: {}", session.originalFilename);

            // Step 1: Load original file
            byte[] originalData = fileStorageService.loadFile(fileId);

            // Step 2: Generate DEK
            SecretKey dek = dekService.generateDek();
            log.info("Generated DEK");

            // Step 3: Encrypt file with DEK
            byte[] encryptedData = fileEncryptionService.encryptFile(originalData, dek);
            log.info("Encrypted file with DEK");

            // Step 4: Encrypt DEK with HSM KEK
            String encryptedDekBase64 = dekService.encryptDekToBase64(dek);
            log.info("Encrypted DEK with HSM KEK");

            // Step 5: Store encrypted file
            String encryptedFileId = fileStorageService.storeFile(
                    encryptedData,
                    session.originalFilename + ".encrypted");

            // Update session
            session.encryptedFileId = encryptedFileId;
            session.encryptedDek = encryptedDekBase64;
            session.encryptedSize = encryptedData.length;

            // Create result
            EncryptionResult result = new EncryptionResult(
                    encryptedFileId,
                    session.originalFilename,
                    session.originalFilename + ".encrypted",
                    session.originalSize,
                    session.encryptedSize,
                    encryptedDekBase64,
                    "File encrypted successfully",
                    true);

            log.info("Encryption completed successfully");
            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            log.error("Error processing encryption", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Encryption failed: " + e.getMessage()));
        }
    }

    /**
     * Download encrypted file
     */
    @GetMapping("/download/file/{fileId}")
    public ResponseEntity<Resource> downloadEncryptedFile(@PathVariable String fileId) {
        try {
            EncryptionSession session = sessions.get(fileId);
            if (session == null || session.encryptedFileId == null) {
                return ResponseEntity.notFound().build();
            }

            byte[] data = fileStorageService.loadFile(session.encryptedFileId);
            ByteArrayResource resource = new ByteArrayResource(data);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + session.originalFilename + ".encrypted\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(data.length)
                    .body(resource);

        } catch (Exception e) {
            log.error("Error downloading encrypted file", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Download encrypted DEK as text file
     */
    @GetMapping("/download/dek/{fileId}")
    public ResponseEntity<Resource> downloadEncryptedDek(@PathVariable String fileId) {
        try {
            EncryptionSession session = sessions.get(fileId);
            if (session == null || session.encryptedDek == null) {
                return ResponseEntity.notFound().build();
            }

            byte[] data = session.encryptedDek.getBytes();
            ByteArrayResource resource = new ByteArrayResource(data);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + session.originalFilename + ".dek\"")
                    .contentType(MediaType.TEXT_PLAIN)
                    .contentLength(data.length)
                    .body(resource);

        } catch (Exception e) {
            log.error("Error downloading encrypted DEK", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Internal class to store encryption session data
     */
    private static class EncryptionSession {
        String fileId;
        String originalFilename;
        long originalSize;
        String encryptedFileId;
        String encryptedDek;
        long encryptedSize;
    }
}
