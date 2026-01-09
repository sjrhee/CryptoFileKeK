package com.crypto.fileencryption.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response model for encryption operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EncryptionResult {
    private String fileId;
    private String originalFilename;
    private String encryptedFilename;
    private long originalSize;
    private long encryptedSize;
    private String encryptedDek; // Base64-encoded encrypted DEK
    private String message;
    private boolean success;
}
