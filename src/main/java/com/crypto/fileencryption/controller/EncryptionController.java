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
import org.springframework.web.bind.annotation.*;

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
     * Select file from server input directory for encryption
     */
    @PostMapping("/select")
    public ResponseEntity<ApiResponse<Map<String, Object>>> selectFile(
            @RequestBody Map<String, String> payload) {
        try {
            String filename = payload.get("filename");
            if (filename == null || filename.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Please provide a filename"));
            }

            log.info("Selected file for encryption: {}", filename);

            // Load file from input directory
            var fileData = fileStorageService.readFromInput(filename);

            // Store in temp session
            var fileId = fileStorageService.storeTemp(fileData, filename);

            // Create session
            var session = new EncryptionSession();
            session.fileId = fileId;
            session.originalFilename = filename;
            session.originalSize = fileData.length;
            sessions.put(fileId, session);

            var response = new HashMap<String, Object>();
            response.put("fileId", fileId);
            response.put("filename", filename);
            response.put("size", fileData.length);

            return ResponseEntity.ok(ApiResponse.success("File selected successfully", response));

        } catch (Exception e) {
            log.error("Error selecting file", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to select file: " + e.getMessage()));
        }
    }

    /**
     * Process encryption
     */
    @PostMapping("/process/{fileId}")
    public ResponseEntity<ApiResponse<EncryptionResult>> processEncryption(
            @PathVariable String fileId) {
        try {
            var session = sessions.get(fileId);
            if (session == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Invalid file ID or session expired"));
            }

            log.info("Processing encryption for file: {}", session.originalFilename);

            // Step 1: Load original file
            var originalData = fileStorageService.loadTemp(fileId);

            // Step 2: Generate DEK
            var dek = dekService.generateDek();
            log.info("Generated DEK");

            // Step 3: Encrypt file with DEK
            var encryptedData = fileEncryptionService.encryptFile(originalData, dek);
            log.info("Encrypted file with DEK");

            // Step 4: Encrypt DEK with HSM KEK
            var encryptedDekBase64 = dekService.encryptDekToBase64(dek);
            log.info("Encrypted DEK with HSM KEK");

            // Step 5: Save to output directory
            String encryptedFilename = session.originalFilename + ".encrypted";
            fileStorageService.writeToOutput(encryptedFilename, encryptedData);
            log.info("Saved encrypted file to output: {}", encryptedFilename);

            // Save DEK to output directory
            String dekFilename = session.originalFilename + ".dek";
            fileStorageService.writeToOutput(dekFilename, encryptedDekBase64.getBytes());
            log.info("Saved DEK to output: {}", dekFilename);

            // Cleanup: Delete the input temp file now that processing is done
            fileStorageService.deleteTemp(fileId);

            // Update session (Only what is needed for simple status, no IDs for download
            // needed now)
            session.encryptedDek = encryptedDekBase64;
            session.encryptedSize = encryptedData.length;
            // session.encryptedFileId is no longer needed/stored in temp

            // Create result
            var result = new EncryptionResult(
                    null,
                    session.originalFilename,
                    session.originalFilename + ".encrypted",
                    session.originalSize,
                    session.encryptedSize,
                    encryptedDekBase64,
                    "File encrypted successfully",
                    true);

            log.info("Encryption completed successfully. Encrypted DEK length: {}", encryptedDekBase64.length());
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
            var session = sessions.get(fileId);
            if (session == null || session.encryptedFileId == null) {
                return ResponseEntity.notFound().build();
            }

            var data = fileStorageService.loadTemp(session.encryptedFileId);
            var resource = new ByteArrayResource(data);

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
            var session = sessions.get(fileId);
            if (session == null || session.encryptedDek == null) {
                return ResponseEntity.notFound().build();
            }

            var data = session.encryptedDek.getBytes();
            var resource = new ByteArrayResource(data);

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
