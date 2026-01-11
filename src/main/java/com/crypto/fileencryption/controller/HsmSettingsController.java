package com.crypto.fileencryption.controller;

import com.crypto.fileencryption.service.HsmRouterService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/hsm")
@RequiredArgsConstructor
public class HsmSettingsController {

    private final HsmRouterService hsmRouterService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> getStatus() {
        Map<String, Boolean> status = new HashMap<>();
        status.put("useHsm", hsmRouterService.isUseHsm());
        return ResponseEntity.ok(status);
    }

    @PostMapping("/config")
    public ResponseEntity<?> configure(@RequestBody HsmConfigRequest request) {
        try {
            hsmRouterService.configure(request.isUseHsm(), request.getPin());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to configure HSM", e);
            return ResponseEntity.internalServerError().body("HSM Configuration Failed: " + e.getMessage());
        }
    }

    @Data
    public static class HsmConfigRequest {
        private boolean useHsm;
        private String pin;
    }
}
