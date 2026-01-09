package com.crypto.fileencryption.controller;

import com.crypto.fileencryption.model.ApiResponse;
import com.crypto.fileencryption.model.DecryptionResult;
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
 * REST Controller for file decryption operations
 */
@Slf4j
@RestController
@RequestMapping("/api/decrypt")
@RequiredArgsConstructor
public class DecryptionController {

    private final DekService dekService;
    private final FileEncryptionService fileEncryptionService;
    private final FileStorageService fileStorageService;

    // Temporary storage for decryption session data
    private final Map<String, DecryptionSession> sessions = new HashMap<>();

    /**
     * Upload encrypted file and DEK for decryption
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadFiles(
            @RequestParam("encryptedFile") MultipartFile encryptedFile,
            @RequestParam("encryptedDek") MultipartFile encryptedDek) {
        try {
            if (encryptedFile.isEmpty() || encryptedDek.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Please provide both encrypted file and DEK"));
            }

            log.info("Received files for decryption: {} ({} bytes)",
                    encryptedFile.getOriginalFilename(), encryptedFile.getSize());

            // Store encrypted file
            byte[] encryptedData = encryptedFile.getBytes();
            String fileId = fileStorageService.storeFile(
                    encryptedData,
                    encryptedFile.getOriginalFilename());

            // Read encrypted DEK
            String encryptedDekBase64 = new String(encryptedDek.getBytes()).trim();

            // Create session
            DecryptionSession session = new DecryptionSession();
            session.fileId = fileId;
            session.originalFilename = encryptedFile.getOriginalFilename();
            session.encryptedSize = encryptedData.length;
            session.encryptedDek = encryptedDekBase64;
            sessions.put(fileId, session);

            Map<String, Object> response = new HashMap<>();
            response.put("fileId", fileId);
            response.put("filename", encryptedFile.getOriginalFilename());
            response.put("size", encryptedFile.getSize());

            return ResponseEntity.ok(ApiResponse.success("Files uploaded successfully", response));

        } catch (Exception e) {
            log.error("Error uploading files", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to upload files: " + e.getMessage()));
        }
    }

    /**
     * Process decryption
     */
    @PostMapping("/process/{fileId}")
    public ResponseEntity<ApiResponse<DecryptionResult>> processDecryption(
            @PathVariable String fileId) {
        try {
            DecryptionSession session = sessions.get(fileId);
            if (session == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Invalid file ID or session expired"));
            }

            log.info("Processing decryption for file: {}", session.originalFilename);

            // Step 1: Load encrypted file
            byte[] encryptedData = fileStorageService.loadFile(fileId);

            // Step 2: Decrypt DEK using HSM KEK
            SecretKey dek = dekService.decryptDekFromBase64(session.encryptedDek);
            log.info("Decrypted DEK with HSM KEK");

            // Step 3: Decrypt file with DEK
            byte[] decryptedData = fileEncryptionService.decryptFile(encryptedData, dek);
            log.info("Decrypted file with DEK");

            // Step 4: Store decrypted file
            String decryptedFilename = session.originalFilename.replace(".encrypted", "");
            String decryptedFileId = fileStorageService.storeFile(
                    decryptedData,
                    decryptedFilename);

            // Update session
            session.decryptedFileId = decryptedFileId;
            session.decryptedSize = decryptedData.length;

            // Create result
            DecryptionResult result = new DecryptionResult(
                    decryptedFileId,
                    decryptedFilename,
                    session.encryptedSize,
                    session.decryptedSize,
                    "File decrypted successfully",
                    true);

            log.info("Decryption completed successfully");
            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            log.error("Error processing decryption", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Decryption failed: " + e.getMessage()));
        }
    }

    /**
     * Download decrypted file
     */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadDecryptedFile(@PathVariable String fileId) {
        try {
            DecryptionSession session = sessions.get(fileId);
            if (session == null || session.decryptedFileId == null) {
                return ResponseEntity.notFound().build();
            }

            byte[] data = fileStorageService.loadFile(session.decryptedFileId);
            ByteArrayResource resource = new ByteArrayResource(data);

            String filename = session.originalFilename.replace(".encrypted", "");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(data.length)
                    .body(resource);

        } catch (Exception e) {
            log.error("Error downloading decrypted file", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Internal class to store decryption session data
     */
    private static class DecryptionSession {
        String fileId;
        String originalFilename;
        long encryptedSize;
        String encryptedDek;
        String decryptedFileId;
        long decryptedSize;
    }
}
