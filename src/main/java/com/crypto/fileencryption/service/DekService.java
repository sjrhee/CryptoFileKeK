package com.crypto.fileencryption.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for DEK (Data Encryption Key) operations.
 * 
 * DEK is used to encrypt the actual file data.
 * The DEK itself is encrypted by the KEK (stored in HSM) before
 * storage/transmission.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DekService {

    private final HsmService hsmService;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final int DEK_KEY_SIZE = 256; // AES-256

    /**
     * Generate a new random DEK
     * 
     * @return Generated DEK
     */
    public SecretKey generateDek() throws Exception {
        log.debug("Generating new DEK with {} bits", DEK_KEY_SIZE);

        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(DEK_KEY_SIZE, secureRandom);
        SecretKey dek = keyGen.generateKey();

        log.debug("DEK generated successfully");
        return dek;
    }

    /**
     * Encrypt DEK using HSM's KEK
     * 
     * @param dek The DEK to encrypt
     * @return Encrypted DEK bytes (IV + ciphertext + tag)
     */
    public byte[] encryptDek(SecretKey dek) throws Exception {
        log.debug("Encrypting DEK with HSM KEK");

        byte[] dekBytes = dek.getEncoded();
        byte[] encryptedDek = hsmService.encryptWithKek(dekBytes);

        log.debug("DEK encrypted successfully. Size: {} bytes", encryptedDek.length);
        return encryptedDek;
    }

    /**
     * Decrypt DEK using HSM's KEK
     * 
     * @param encryptedDek Encrypted DEK bytes (IV + ciphertext + tag)
     * @return Decrypted DEK
     */
    public SecretKey decryptDek(byte[] encryptedDek) throws Exception {
        log.debug("Decrypting DEK with HSM KEK");

        byte[] dekBytes = hsmService.decryptWithKek(encryptedDek);
        SecretKey dek = new javax.crypto.spec.SecretKeySpec(dekBytes, "AES");

        log.debug("DEK decrypted successfully");
        return dek;
    }

    /**
     * Encrypt DEK and return as Base64 string for easy transmission
     * 
     * @param dek The DEK to encrypt
     * @return Base64-encoded encrypted DEK
     */
    public String encryptDekToBase64(SecretKey dek) throws Exception {
        byte[] encryptedDek = encryptDek(dek);
        return Base64.getEncoder().encodeToString(encryptedDek);
    }

    /**
     * Decrypt DEK from Base64 string
     * 
     * @param encryptedDekBase64 Base64-encoded encrypted DEK
     * @return Decrypted DEK
     */
    public SecretKey decryptDekFromBase64(String encryptedDekBase64) throws Exception {
        byte[] encryptedDek = Base64.getDecoder().decode(encryptedDekBase64);
        return decryptDek(encryptedDek);
    }

    /**
     * Destroy the plaintext DEK explicitly if possible
     * Note: Standard Java SecretKeySpec might not support this, but good practice.
     */
    public void destroyDek(SecretKey dek) {
        if (dek == null)
            return;
        try {
            if (!dek.isDestroyed()) {
                dek.destroy();
                log.debug("DEK explicitly destroyed");
            }
        } catch (javax.security.auth.DestroyFailedException e) {
            log.warn("DEK does not support explicit destruction (likely standard SecretKeySpec/immutable)", e);
        } catch (Exception e) {
            log.warn("Error destroying DEK", e);
        }
    }
}
