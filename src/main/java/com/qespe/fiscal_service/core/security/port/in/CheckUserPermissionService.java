package com.qespe.fiscal_service.core.security.port.in;

import java.util.UUID;

public interface CheckUserPermissionService {
    boolean hasPermission(UUID userId, UUID companyId, String permissionKey);
}