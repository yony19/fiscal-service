package com.qespe.fiscal_service.shared.security;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Getter
@Component
public class JwtProperties {

    private final String secret;
    private final long expirationMinutes;

    public JwtProperties() {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        this.secret = dotenv.get("JWT_SECRET");
        if (this.secret == null || this.secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters. " +
                    "Set it in a .env file or as an environment variable.");
        }

        this.expirationMinutes = Long.parseLong(dotenv.get("JWT_EXPIRATION_MINUTES", "60"));
    }

}
