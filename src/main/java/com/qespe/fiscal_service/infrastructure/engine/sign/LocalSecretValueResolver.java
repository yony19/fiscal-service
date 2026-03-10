package com.qespe.fiscal_service.infrastructure.engine.sign;

import org.springframework.stereotype.Component;

@Component
public class LocalSecretValueResolver implements SecretValueResolver {

    @Override
    public String resolve(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }

        String ref = reference.trim();
        if (ref.startsWith("env:")) {
            String key = ref.substring(4);
            return System.getenv(key);
        }
        if (ref.startsWith("plain:")) {
            return ref.substring(6);
        }
        return ref;
    }
}
