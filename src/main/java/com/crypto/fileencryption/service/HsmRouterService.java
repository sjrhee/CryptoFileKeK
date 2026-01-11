package com.crypto.fileencryption.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Routes HSM operations to either the Real HSM or Simulated HSM based on
 * configuration.
 * Also handles dynamic login/logout for the Real HSM.
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class HsmRouterService implements HsmService {

    private final RealHsmService realHsmService;
    private final SimulatedHsmService simulatedHsmService;

    private boolean useHsm = false;

    /**
     * Configure HSM usage.
     * 
     * @param useHsm Whether to use the Real HSM
     * @param pin    The PIN for the Real HSM (required if useHsm is true)
     * @throws Exception If login fails
     */
    public synchronized void configure(boolean useHsm, String pin) throws Exception {
        if (useHsm) {
            if (pin == null || pin.trim().isEmpty()) {
                throw new IllegalArgumentException("PIN is required to enable Real HSM");
            }
            log.info("Switching to Real HSM mode...");
            realHsmService.login(pin);
            this.useHsm = true;
            log.info("Switched to Real HSM mode successfully.");
        } else {
            log.info("Switching to Simulated HSM mode...");
            realHsmService.logout();
            this.useHsm = false;
            log.info("Switched to Simulated HSM mode.");
        }
    }

    public boolean isUseHsm() {
        return useHsm;
    }

    @Override
    public byte[] encryptWithKek(byte[] plaintext) throws Exception {
        if (useHsm) {
            return realHsmService.encryptWithKek(plaintext);
        } else {
            return simulatedHsmService.encryptWithKek(plaintext);
        }
    }

    @Override
    public byte[] decryptWithKek(byte[] ciphertext) throws Exception {
        if (useHsm) {
            return realHsmService.decryptWithKek(ciphertext);
        } else {
            return simulatedHsmService.decryptWithKek(ciphertext);
        }
    }

    @Override
    public String getKekAlgorithm() {
        if (useHsm) {
            return realHsmService.getKekAlgorithm();
        } else {
            return simulatedHsmService.getKekAlgorithm();
        }
    }

    @Override
    public int getKekKeySize() {
        if (useHsm) {
            return realHsmService.getKekKeySize();
        } else {
            return simulatedHsmService.getKekKeySize();
        }
    }
}
