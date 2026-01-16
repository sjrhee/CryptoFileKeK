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
     * Select encrypted file and DEK for decryption
     */
    @PostMapping("/select")
    public ResponseEntity<ApiResponse<Map<String, Object>>> selectFiles(
            @RequestBody Map<String, String> payload) {
        try {
            String encryptedFilename = payload.get("encryptedFilename");
            String dekFilename = payload.get("dekFilename");

            if (encryptedFilename == null || dekFilename == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Please provide both encrypted file and DEK filename"));
            }

            log.info("Selected files for decryption: {}, {}", encryptedFilename, dekFilename);

            // Load encrypted file
            var encryptedData = fileStorageService.readFromInput(encryptedFilename);
            var fileId = fileStorageService.storeTemp(encryptedData, encryptedFilename);

            // Read encrypted DEK
            var dekData = fileStorageService.readFromInput(dekFilename);
            var encryptedDekBase64 = new String(dekData).trim();

            // Create session
            var session = new DecryptionSession();
            session.fileId = fileId;
            session.originalFilename = encryptedFilename;
            session.encryptedSize = encryptedData.length;
            session.encryptedDek = encryptedDekBase64;
            sessions.put(fileId, session);

            var response = new HashMap<String, Object>();
            response.put("fileId", fileId);
            response.put("filename", encryptedFilename);
            response.put("size", encryptedData.length);

            return ResponseEntity.ok(ApiResponse.success("Files selected successfully", response));

        } catch (Exception e) {
            log.error("Error selecting files", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to select files: " + e.getMessage()));
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
            var encryptedData = fileStorageService.loadTemp(fileId);

            // Step 2: Decrypt DEK using HSM KEK
            var dek = dekService.decryptDekFromBase64(session.encryptedDek);
            log.info("Decrypted DEK with HSM KEK");

            // Step 3: Decrypt file with DEK
            var decryptedData = fileEncryptionService.decryptFile(encryptedData, dek);
            log.info("Decrypted file with DEK");

            // SECURITY: Destroy plaintext DEK immediately after use
            dekService.destroyDek(dek);

            // Step 4: Save decrypted file to output
            String decryptedFilename = session.originalFilename.replace(".encrypted", "");
            if (decryptedFilename.equals(session.originalFilename)) {
                decryptedFilename = "decrypted_" + session.originalFilename;
            }
            fileStorageService.writeToOutput(decryptedFilename, decryptedData);

            // Store in temp for consistency if needed
            var decryptedFileId = fileStorageService.storeTemp(
                    decryptedData,
                    decryptedFilename);

            // Update session
            session.decryptedFileId = decryptedFileId;
            session.decryptedSize = decryptedData.length;

            // Create result
            var result = new DecryptionResult(
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

            var data = fileStorageService.loadTemp(session.decryptedFileId);
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
