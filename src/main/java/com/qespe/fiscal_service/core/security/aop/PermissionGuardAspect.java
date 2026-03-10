package com.qespe.fiscal_service.core.security.aop;

import com.qespe.fiscal_service.core.security.annotation.RequirePermission;
import com.qespe.fiscal_service.core.security.port.in.CheckUserPermissionService;
import com.qespe.fiscal_service.infrastructure.security.util.SecurityUtils;
import com.qespe.fiscal_service.shared.exception.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class PermissionGuardAspect {

    private final CheckUserPermissionService checkUserPermissionService;
    private final SecurityUtils securityUtils;

    @Before("@annotation(requirePermission)")
    public void checkPermission(RequirePermission requirePermission) {

        UUID userId = securityUtils.getCurrentUserId();
        UUID companyId = securityUtils.getCurrentCompanyId();

        if (companyId == null) {
            throw new AccessDeniedException("Debes seleccionar una compañía antes de continuar.");
        }

        String permission = requirePermission.value();
        if (!checkUserPermissionService.hasPermission(userId, companyId, permission)) {
            throw new AccessDeniedException("No tienes el permiso requerido: " + permission);
        }
    }
}