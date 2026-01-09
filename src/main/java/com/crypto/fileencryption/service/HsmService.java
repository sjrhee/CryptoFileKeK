package com.crypto.fileencryption.service;

/**
 * Interface for HSM (Hardware Security Module) operations.
 * This interface defines the contract for KEK (Key Encryption Key) operations.
 * 
 * Implementation can be:
 * - SimulatedHsmService: For development and testing
 * - Actual HSM provider implementations (Luna, CloudHSM, etc.)
 */
public interface HsmService {

    /**
     * Encrypt data using the KEK stored in HSM
     * 
     * @param plaintext Data to encrypt
     * @return Encrypted data (IV + ciphertext + tag)
     */
    byte[] encryptWithKek(byte[] plaintext) throws Exception;

    /**
     * Decrypt data using the KEK stored in HSM
     * 
     * @param ciphertext Encrypted data (IV + ciphertext + tag)
     * @return Decrypted data
     */
    byte[] decryptWithKek(byte[] ciphertext) throws Exception;

    /**
     * Get the KEK algorithm
     * 
     * @return Algorithm name (e.g., "AES")
     */
    String getKekAlgorithm();

    /**
     * Get the KEK key size in bits
     * 
     * @return Key size (e.g., 256)
     */
    int getKekKeySize();
}
