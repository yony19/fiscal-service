package com.qespe.fiscal_service.infrastructure.security.util;

import com.qespe.fiscal_service.shared.exception.AccessDeniedException;
import com.qespe.fiscal_service.shared.security.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final JwtProvider jwtProvider;

    public UUID getCurrentUserId() {
        return jwtProvider.extractUserId(getTokenOrThrow());
    }

    public UUID getCurrentCompanyId() {
        return jwtProvider.extractCompanyId(getTokenOrThrow());
    }

    public String getRawToken() {
        return getTokenOrThrow();
    }

    private String getTokenOrThrow() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new AccessDeniedException("No hay contexto HTTP activo.");
        }

        HttpServletRequest request = attrs.getRequest();
        String token = jwtProvider.resolveToken(request);
        if (token == null) {
            throw new AccessDeniedException("Token JWT ausente o inválido.");
        }
        return token;
    }
}