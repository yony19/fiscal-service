package com.qespe.fiscal_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@ConfigurationProperties(prefix = "fiscal.artifacts")
public class FiscalArtifactProperties {

    private String storageMode = "FILE_SYSTEM";
    private String basePath = "./artifacts/fiscal";

    public String getStorageMode() {
        return storageMode;
    }

    public void setStorageMode(String storageMode) {
        this.storageMode = storageMode;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public Path resolvedBasePath() {
        return Paths.get(basePath).normalize().toAbsolutePath();
    }
}
