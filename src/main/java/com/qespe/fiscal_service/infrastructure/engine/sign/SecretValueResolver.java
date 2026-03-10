package com.qespe.fiscal_service.infrastructure.engine.sign;

public interface SecretValueResolver {
    String resolve(String reference);
}
