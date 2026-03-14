package com.qespe.fiscal_service.infrastructure.engine.storage;

import com.qespe.fiscal_service.config.FiscalArtifactProperties;
import com.qespe.fiscal_service.core.domain.engine.StoredArtifactResult;
import com.qespe.fiscal_service.core.port.out.FiscalArtifactStoragePort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class FileSystemFiscalArtifactStorage implements FiscalArtifactStoragePort {

    private final FiscalArtifactProperties properties;
    private final Path basePath;
    private final Path xmlPath;
    private final Path signedPath;
    private final Path zipPath;
    private final Path responsePath;
    private final Path cdrPath;
    private final Path logsPath;

    public FileSystemFiscalArtifactStorage(FiscalArtifactProperties properties) {
        this.properties = properties;
        this.basePath = properties.resolvedBasePath();
        this.xmlPath = basePath.resolve("xml");
        this.signedPath = basePath.resolve("signed");
        this.zipPath = basePath.resolve("zip");
        this.responsePath = basePath.resolve("responses");
        this.cdrPath = basePath.resolve("cdr");
        this.logsPath = basePath.resolve("logs");
        createDirectories();
    }

    @Override
    public StoredArtifactResult storeXml(FiscalDocumentEntity document, String xmlContent) {
        return store(document, xmlContent, xmlPath, sanitizeFilename(document.getFullNumber()) + ".xml");
    }

    @Override
    public StoredArtifactResult storeSignedXml(FiscalDocumentEntity document, String signedXmlContent) {
        return store(document, signedXmlContent, signedPath, sanitizeFilename(document.getFullNumber()) + "-signed.xml");
    }

    @Override
    public StoredArtifactResult storeZip(FiscalDocumentEntity document, byte[] zipContent) {
        return storeBytes(document, zipContent, zipPath, sanitizeFilename(document.getFullNumber()) + ".zip");
    }

    @Override
    public StoredArtifactResult storeResponse(FiscalDocumentEntity document, String responseContent) {
        return store(document, responseContent, responsePath, sanitizeFilename(document.getFullNumber()) + "-response.xml");
    }

    @Override
    public StoredArtifactResult storeCdr(FiscalDocumentEntity document, byte[] cdrZipContent) {
        return storeBytes(document, cdrZipContent, cdrPath, sanitizeFilename(document.getFullNumber()) + "-cdr.zip");
    }

    private StoredArtifactResult store(FiscalDocumentEntity document, String content, Path rootDir, String filename) {
        return storeBytes(document, content.getBytes(StandardCharsets.UTF_8), rootDir, filename);
    }

    private StoredArtifactResult storeBytes(FiscalDocumentEntity document, byte[] bytes, Path rootDir, String filename) {
        if (!"FILE_SYSTEM".equalsIgnoreCase(properties.getStorageMode())) {
            throw new BusinessException("Unsupported fiscal artifacts storage mode: " + properties.getStorageMode());
        }

        Path file = rootDir.resolve(filename).normalize();

        if (!file.startsWith(rootDir)) {
            throw new BusinessException("Invalid artifact path generated");
        }

        try {
            Files.write(file, bytes);
            return new StoredArtifactResult(file.toString(), sha256(bytes), bytes.length);
        } catch (IOException ex) {
            throw new BusinessException("Unable to store fiscal XML artifact");
        }
    }

    private void createDirectories() {
        try {
            Files.createDirectories(xmlPath);
            Files.createDirectories(signedPath);
            Files.createDirectories(zipPath);
            Files.createDirectories(responsePath);
            Files.createDirectories(cdrPath);
            Files.createDirectories(logsPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to initialize fiscal artifacts directories", ex);
        }
    }

    private String sanitizeFilename(String value) {
        String cleaned = value == null ? "document" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (cleaned.isBlank()) {
            return "document";
        }
        return cleaned;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }
}
