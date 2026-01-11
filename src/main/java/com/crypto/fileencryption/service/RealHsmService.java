package com.crypto.fileencryption.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import safenet.jcprov.*;
import safenet.jcprov.constants.*;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;

/**
 * Real HSM Service implementation using SafeNet JCProv.
 * Connects to a physical or simulated HSM via PKCS#11.
 */
@Slf4j
@Service
// @Primary removed - HsmRouterService will be primary
public class RealHsmService implements HsmService {

    private static final long SLOT_ID = 0;
    private static final String KEK_LABEL = "mk";

    private CK_SESSION_HANDLE session;
    private boolean isLoggedIn = false;

    @PostConstruct
    public void init() {
        try {
            log.info("Initializing Real HSM Service (Session Only)...");

            // 1. Initialize Library
            CK_C_INITIALIZE_ARGS initArgs = new CK_C_INITIALIZE_ARGS(0);
            CryptokiEx.C_Initialize(initArgs);

            // 2. Open Session
            session = new CK_SESSION_HANDLE();
            CryptokiEx.C_OpenSession(SLOT_ID, CKF.RW_SESSION | CKF.SERIAL_SESSION, null, null, session);
            log.info("HSM Session opened on slot {}", SLOT_ID);

            // No auto-login here anymore

        } catch (Exception e) {
            log.error("Failed to initialize HSM connection", e);
            throw new RuntimeException("HSM Initialization Failed", e);
        }
    }

    public synchronized void login(String pin) throws Exception {
        if (isLoggedIn) {
            log.info("Already logged in. Re-verifying...");
            verifyKekAccess();
            return;
        }

        log.info("Attempting HSM Login with provided PIN on slot {}", SLOT_ID);
        CryptokiEx.C_Login(session, CKU.USER, pin.getBytes(StandardCharsets.US_ASCII), pin.length());
        isLoggedIn = true;
        log.info("Logged into HSM successfully");

        verifyKekAccess();
    }

    public synchronized void logout() {
        if (!isLoggedIn)
            return;

        try {
            Cryptoki.C_Logout(session);
            isLoggedIn = false;
            log.info("Logged out from HSM");
        } catch (Exception e) {
            log.warn("Error during logout", e);
        }
    }

    private void verifyKekAccess() throws Exception {
        CK_OBJECT_HANDLE hKek = findKeyHandle(KEK_LABEL);
        log.info("KEK Check Passed: Found KEK '{}' with handle ID: {}", KEK_LABEL, hKek.longValue());
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (isLoggedIn) {
                logout(); // Use the new logout method
            }
            Cryptoki.C_CloseSession(session);
            Cryptoki.C_Finalize(null);
            log.info("HSM Service shutdown complete");
        } catch (Exception e) {
            log.warn("Error during HSM cleanup", e);
        }
    }

    @Override
    public byte[] encryptWithKek(byte[] plaintext) throws Exception {
        log.debug("Wrapping key with HSM...");

        CK_OBJECT_HANDLE hKek = findKeyHandle(KEK_LABEL);
        log.debug("Encrypting: Using KEK Handle: {}", hKek.longValue());

        CK_OBJECT_HANDLE hDek = null;

        try {
            // 1. Create a temporary object for the plaintext DEK
            hDek = createTemporaryKeyObject(plaintext);
            log.debug("Encrypting: Created Temp DEK Handle: {}", hDek.longValue());

            // 2. Wrap the key
            CK_MECHANISM mechanism = new CK_MECHANISM(CKM.AES_KW);
            LongRef wrappedLen = new LongRef();

            // Get size first
            CryptokiEx.C_WrapKey(session, mechanism, hKek, hDek, null, wrappedLen);

            byte[] wrappedBytes = new byte[(int) wrappedLen.value];
            CryptokiEx.C_WrapKey(session, mechanism, hKek, hDek, wrappedBytes, wrappedLen);

            log.debug("Wrap successful. Length: {}", wrappedBytes.length);
            return wrappedBytes;

        } finally {
            if (hDek != null) {
                try {
                    CryptokiEx.C_DestroyObject(session, hDek);
                } catch (Exception e) {
                    log.warn("Failed to destroy temp DEK object", e);
                }
            }
        }
    }

    @Override
    public byte[] decryptWithKek(byte[] ciphertext) throws Exception {
        log.debug("Unwrapping key with HSM...");

        CK_OBJECT_HANDLE hKek = findKeyHandle(KEK_LABEL);
        log.debug("Decrypting: Using KEK Handle: {}", hKek.longValue());

        CK_OBJECT_HANDLE hDek = new CK_OBJECT_HANDLE();

        try {
            // 1. Prepare template for the unwrapped key
            CK_ATTRIBUTE[] template = {
                    new CK_ATTRIBUTE(CKA.CLASS, CKO.SECRET_KEY),
                    new CK_ATTRIBUTE(CKA.KEY_TYPE, CKK.AES),
                    new CK_ATTRIBUTE(CKA.SENSITIVE, CK_BBOOL.FALSE), // Allow reading value
                    new CK_ATTRIBUTE(CKA.EXTRACTABLE, CK_BBOOL.TRUE),
                    new CK_ATTRIBUTE(CKA.TOKEN, CK_BBOOL.FALSE) // Session object
            };

            // 2. Unwrap the key
            CK_MECHANISM mechanism = new CK_MECHANISM(CKM.AES_KW);
            CryptokiEx.C_UnwrapKey(session, mechanism, hKek, ciphertext, ciphertext.length,
                    template, template.length, hDek);

            log.debug("Unwrap successful. New DEK Handle: {}", hDek.longValue());

            // 3. Extract the value (plaintext DEK)
            // Initialize with null to get size first
            CK_ATTRIBUTE[] getAttributes = {
                    new CK_ATTRIBUTE(CKA.VALUE, (Object) null)
            };

            // Get size
            CryptokiEx.C_GetAttributeValue(session, hDek, getAttributes, getAttributes.length);

            // Allocate buffer (valueLen is long)
            getAttributes[0].pValue = new byte[(int) getAttributes[0].valueLen];

            // Get value
            CryptokiEx.C_GetAttributeValue(session, hDek, getAttributes, getAttributes.length);

            return (byte[]) getAttributes[0].pValue;

        } finally {
            if (hDek != null && hDek.longValue() != 0) {
                try {
                    CryptokiEx.C_DestroyObject(session, hDek);
                } catch (Exception e) {
                    log.warn("Failed to destroy unwrapped DEK object", e);
                }
            }
        }
    }

    @Override
    public String getKekAlgorithm() {
        return "AES";
    }

    @Override
    public int getKekKeySize() {
        return 256;
    }

    // Helper: Find Key Handle by Label
    private CK_OBJECT_HANDLE findKeyHandle(String label) throws Exception {
        CK_ATTRIBUTE[] template = {
                new CK_ATTRIBUTE(CKA.CLASS, CKO.SECRET_KEY),
                new CK_ATTRIBUTE(CKA.LABEL, label.getBytes(StandardCharsets.US_ASCII))
        };

        CryptokiEx.C_FindObjectsInit(session, template, template.length);

        CK_OBJECT_HANDLE[] handles = new CK_OBJECT_HANDLE[1];
        handles[0] = new CK_OBJECT_HANDLE(); // IMPORTANT: Initialize the object!

        LongRef count = new LongRef();

        CryptokiEx.C_FindObjects(session, handles, 1, count);
        CryptokiEx.C_FindObjectsFinal(session);

        if (count.value == 0) {
            throw new RuntimeException("KEK with label '" + label + "' not found in HSM");
        }

        return handles[0];
    }

    // Helper: Create Temporary Key Object for Wrapping
    private CK_OBJECT_HANDLE createTemporaryKeyObject(byte[] keyBytes) throws Exception {
        CK_OBJECT_HANDLE hKey = new CK_OBJECT_HANDLE();

        CK_ATTRIBUTE[] template = {
                new CK_ATTRIBUTE(CKA.CLASS, CKO.SECRET_KEY),
                new CK_ATTRIBUTE(CKA.KEY_TYPE, CKK.AES),
                new CK_ATTRIBUTE(CKA.TOKEN, CK_BBOOL.FALSE), // Session object
                new CK_ATTRIBUTE(CKA.VALUE, keyBytes, keyBytes.length),
                new CK_ATTRIBUTE(CKA.SENSITIVE, CK_BBOOL.FALSE),
                new CK_ATTRIBUTE(CKA.EXTRACTABLE, CK_BBOOL.TRUE) // Allow it to be wrapped
        };

        CryptokiEx.C_CreateObject(session, template, template.length, hKey);
        return hKey;
    }
}
