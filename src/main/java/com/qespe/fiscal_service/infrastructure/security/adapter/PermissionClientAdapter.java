package com.qespe.fiscal_service.infrastructure.security.adapter;

import com.qespe.fiscal_service.core.security.port.in.CheckUserPermissionService;
import com.qespe.fiscal_service.infrastructure.rest.client.PermissionRestClient;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PermissionClientAdapter implements CheckUserPermissionService {

    private final RedisTemplate<String, Set<String>> redisTemplate;
    private final PermissionRestClient restClient;

    private static final String KEY_TEMPLATE = "permissions:%s:%s";

    @Override
    public boolean hasPermission(UUID userId, UUID companyId, String permissionKey) {
        String key = String.format(KEY_TEMPLATE, userId, companyId);
        Set<String> cached = redisTemplate.opsForValue().get(key);

        if (cached != null) return cached.contains(permissionKey);

        Set<String> permissions = restClient.getPermissions(userId, companyId);
        redisTemplate.opsForValue().set(key, permissions, Duration.ofHours(6));
        return permissions.contains(permissionKey);
    }
}