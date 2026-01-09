package com.crypto.fileencryption.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response model for decryption operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DecryptionResult {
    private String fileId;
    private String originalFilename;
    private long encryptedSize;
    private long decryptedSize;
    private String message;
    private boolean success;
}
