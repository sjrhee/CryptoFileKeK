package com.crypto.fileencryption.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Service for managing temporary file storage.
 * Handles uploaded files, encrypted files, and decrypted files.
 */
@Slf4j
@Service
public class FileStorageService {

    @Value("${file.storage.location:./temp-storage}")
    private String storageLocation;

    private Path rootLocation;

    @PostConstruct
    public void init() {
        try {
            rootLocation = Paths.get(storageLocation);
            Files.createDirectories(rootLocation);
            log.info("File storage initialized at: {}", rootLocation.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to initialize file storage", e);
            throw new RuntimeException("Could not initialize storage", e);
        }
    }

    /**
     * Store file data with a unique identifier
     * 
     * @param data             File data
     * @param originalFilename Original filename
     * @return Unique file identifier
     */
    public String storeFile(byte[] data, String originalFilename) throws IOException {
        String fileId = UUID.randomUUID().toString();
        Path filePath = rootLocation.resolve(fileId);
        Files.write(filePath, data);

        log.info("Stored file: {} (original: {})", fileId, originalFilename);
        return fileId;
    }

    /**
     * Load file data by identifier
     * 
     * @param fileId File identifier
     * @return File data
     */
    public byte[] loadFile(String fileId) throws IOException {
        Path filePath = rootLocation.resolve(fileId);

        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + fileId);
        }

        byte[] data = Files.readAllBytes(filePath);
        log.debug("Loaded file: {} ({} bytes)", fileId, data.length);
        return data;
    }

    /**
     * Delete file by identifier
     * 
     * @param fileId File identifier
     */
    public void deleteFile(String fileId) {
        try {
            Path filePath = rootLocation.resolve(fileId);
            Files.deleteIfExists(filePath);
            log.debug("Deleted file: {}", fileId);
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", fileId, e);
        }
    }

    /**
     * Clean up old files (can be scheduled)
     */
    public void cleanupOldFiles() {
        try {
            Files.list(rootLocation)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            log.debug("Cleaned up file: {}", path.getFileName());
                        } catch (IOException e) {
                            log.warn("Failed to delete file during cleanup: {}", path, e);
                        }
                    });
            log.info("File cleanup completed");
        } catch (IOException e) {
            log.error("Failed to cleanup files", e);
        }
    }

    /**
     * Get storage root path
     */
    public Path getRootLocation() {
        return rootLocation;
    }
}
