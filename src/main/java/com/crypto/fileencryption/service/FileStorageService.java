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

    @Value("${file.storage.location:./DATA}")
    private String baseLocation;

    private Path inputLocation;
    private Path outputLocation;
    private Path tempLocation;

    @PostConstruct
    public void init() {
        try {
            Path root = Paths.get(baseLocation).toAbsolutePath().normalize();
            inputLocation = root;
            outputLocation = root;
            tempLocation = root.resolve(".temp");

            Files.createDirectories(inputLocation);
            Files.createDirectories(outputLocation);
            Files.createDirectories(tempLocation);

            log.info("File storage initialized.");
            log.info("Base Root (DATA): {}", root);
            log.info("Input/Output: {}", inputLocation);
        } catch (IOException e) {
            log.error("Failed to initialize file storage", e);
            throw new RuntimeException("Could not initialize storage", e);
        }
    }

    /**
     * List all files in input directory
     */
    public java.util.List<String> listInputFiles() throws IOException {
        try (var stream = Files.list(inputLocation)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    /**
     * Read file from input directory
     */
    public byte[] readFromInput(String filename) throws IOException {
        var filePath = inputLocation.resolve(filename).normalize();
        if (!filePath.startsWith(inputLocation)) {
            throw new SecurityException("Invalid file path");
        }
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + filename);
        }
        return Files.readAllBytes(filePath);
    }

    /**
     * Write data to output directory
     */
    public void writeToOutput(String filename, byte[] data) throws IOException {
        var filePath = outputLocation.resolve(filename).normalize();
        if (!filePath.startsWith(outputLocation)) {
            throw new SecurityException("Invalid file path");
        }
        Files.write(filePath, data);
        log.info("Saved result to: {}", filePath);
    }

    // Legacy temp storage support for internal processing if needed
    public String storeTemp(byte[] data, String originalFilename) throws IOException {
        var fileId = UUID.randomUUID().toString();
        var filePath = tempLocation.resolve(fileId);
        Files.write(filePath, data);
        return fileId;
    }

    public byte[] loadTemp(String fileId) throws IOException {
        var filePath = tempLocation.resolve(fileId);
        return Files.readAllBytes(filePath);
    }
}
