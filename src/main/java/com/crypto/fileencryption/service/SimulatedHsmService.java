package com.crypto.fileencryption.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Simulated HSM Service for development and testing.
 * 
 * This implementation simulates HSM operations using local cryptography.
 * In production, replace this with actual HSM provider implementation:
 * 
 * For Thales Luna HSM:
 * - Use Luna JCProv provider
 * - Configure partition and credentials
 * - Access KEK via slot/token
 * 
 * For AWS CloudHSM:
 * - Use AWS CloudHSM JCE provider
 * - Configure cluster and credentials
 * - Access KEK via key handle
 * 
 * For Azure Key Vault:
 * - Use Azure Key Vault SDK
 * - Configure vault URL and credentials
 * - Access KEK via key identifier
 */
@Slf4j
@Service
public class SimulatedHsmService implements HsmService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_SIZE = 12; // 96 bits for GCM
    private static final int TAG_SIZE = 128; // 128 bits authentication tag

    @Value("${hsm.kek.keySize:256}")
    private int kekKeySize;

    private SecretKey kek;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() throws Exception {
        // In a real HSM, the KEK would be stored securely in the HSM
        // and never exposed. This is just for simulation.
        log.info("Initializing simulated HSM service");
        log.warn("WARNING: Using simulated HSM. Replace with actual HSM in production!");

        // Generate a simulated KEK
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(kekKeySize, secureRandom);
        this.kek = keyGen.generateKey();

        log.info("Simulated KEK initialized with {} bits", kekKeySize);
    }

    @Override
    public byte[] encryptWithKek(byte[] plaintext) throws Exception {
        log.debug("Encrypting {} bytes with KEK", plaintext.length);

        // Generate random IV
        byte[] iv = new byte[IV_SIZE];
        secureRandom.nextBytes(iv);

        // Initialize cipher
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_SIZE, iv);
        cipher.init(Cipher.ENCRYPT_MODE, kek, parameterSpec);

        // Encrypt
        byte[] ciphertext = cipher.doFinal(plaintext);

        // Combine IV + ciphertext (ciphertext already includes GCM tag)
        byte[] result = new byte[IV_SIZE + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, IV_SIZE);
        System.arraycopy(ciphertext, 0, result, IV_SIZE, ciphertext.length);

        log.debug("Encryption complete. Output size: {} bytes", result.length);
        return result;
    }

    @Override
    public byte[] decryptWithKek(byte[] ciphertext) throws Exception {
        log.debug("Decrypting {} bytes with KEK", ciphertext.length);

        if (ciphertext.length < IV_SIZE) {
            throw new IllegalArgumentException("Ciphertext too short");
        }

        // Extract IV and encrypted data
        byte[] iv = Arrays.copyOfRange(ciphertext, 0, IV_SIZE);
        byte[] encryptedData = Arrays.copyOfRange(ciphertext, IV_SIZE, ciphertext.length);

        // Initialize cipher
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_SIZE, iv);
        cipher.init(Cipher.DECRYPT_MODE, kek, parameterSpec);

        // Decrypt
        byte[] plaintext = cipher.doFinal(encryptedData);

        log.debug("Decryption complete. Output size: {} bytes", plaintext.length);
        return plaintext;
    }

    @Override
    public String getKekAlgorithm() {
        return "AES";
    }

    @Override
    public int getKekKeySize() {
        return kekKeySize;
    }

    /**
     * For testing purposes only - get the KEK bytes
     * In a real HSM, this would NEVER be possible!
     */
    public byte[] getKekBytesForTesting() {
        return kek.getEncoded();
    }
}
