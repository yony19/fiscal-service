package com.qespe.fiscal_service.infrastructure.rest.client;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionRestClient {

    private final RestTemplate restTemplate;
    @Value("${auth-service.base-url}")
    private String baseUrl;

    public Set<String> getPermissions(UUID userId, UUID companyId) {
        String url = String.format("%s/internal/permissions?userId=%s&companyId=%s",
                baseUrl, userId, companyId);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("Authorization", getAuthorizationHeader()); // 🔐 Importante

        try {
            ResponseEntity<Set<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            return response.getBody() != null ? response.getBody() : Collections.emptySet();
        } catch (Exception ex) {
            log.error("Error consultando permisos desde auth-service", ex);
            return Collections.emptySet();
        }
    }

    private String getAuthorizationHeader() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new IllegalStateException("No se pudo obtener el request actual para extraer el token.");
        }

        HttpServletRequest request = attrs.getRequest();
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new IllegalStateException("Token JWT ausente o malformado en el request.");
        }

        return header;
    }
}