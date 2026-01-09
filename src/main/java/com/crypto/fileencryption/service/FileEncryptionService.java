package com.crypto.fileencryption.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Service for file encryption and decryption operations.
 * 
 * Uses AES-256-GCM for authenticated encryption of file data.
 */
@Slf4j
@Service
public class FileEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_SIZE = 12; // 96 bits for GCM
    private static final int TAG_SIZE = 128; // 128 bits authentication tag

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Encrypt file data using the provided DEK
     * 
     * @param fileData Original file data
     * @param dek      Data Encryption Key
     * @return Encrypted data (IV + ciphertext + tag)
     */
    public byte[] encryptFile(byte[] fileData, SecretKey dek) throws Exception {
        log.debug("Encrypting file data. Size: {} bytes", fileData.length);

        // Generate random IV
        var iv = new byte[IV_SIZE];
        secureRandom.nextBytes(iv);

        // Initialize cipher
        var cipher = Cipher.getInstance(ALGORITHM);
        var parameterSpec = new GCMParameterSpec(TAG_SIZE, iv);
        cipher.init(Cipher.ENCRYPT_MODE, dek, parameterSpec);

        // Encrypt
        var ciphertext = cipher.doFinal(fileData);

        // Combine IV + ciphertext (ciphertext already includes GCM tag)
        var result = new byte[IV_SIZE + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, IV_SIZE);
        System.arraycopy(ciphertext, 0, result, IV_SIZE, ciphertext.length);

        log.info("File encrypted successfully. Original size: {} bytes, Encrypted size: {} bytes",
                fileData.length, result.length);
        return result;
    }

    /**
     * Decrypt file data using the provided DEK
     * 
     * @param encryptedData Encrypted file data (IV + ciphertext + tag)
     * @param dek           Data Encryption Key
     * @return Decrypted file data
     */
    public byte[] decryptFile(byte[] encryptedData, SecretKey dek) throws Exception {
        log.debug("Decrypting file data. Size: {} bytes", encryptedData.length);

        if (encryptedData.length < IV_SIZE) {
            throw new IllegalArgumentException("Encrypted data too short");
        }

        // Extract IV and encrypted data
        var iv = Arrays.copyOfRange(encryptedData, 0, IV_SIZE);
        var ciphertext = Arrays.copyOfRange(encryptedData, IV_SIZE, encryptedData.length);

        // Initialize cipher
        var cipher = Cipher.getInstance(ALGORITHM);
        var parameterSpec = new GCMParameterSpec(TAG_SIZE, iv);
        cipher.init(Cipher.DECRYPT_MODE, dek, parameterSpec);

        // Decrypt
        var plaintext = cipher.doFinal(ciphertext);

        log.info("File decrypted successfully. Encrypted size: {} bytes, Decrypted size: {} bytes",
                encryptedData.length, plaintext.length);
        return plaintext;
    }
}
