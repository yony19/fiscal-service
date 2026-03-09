package com.qespe.fiscal_service.infrastructure.mapper;

import com.qespe.fiscal_service.core.dto.provider.FiscalProviderConfigRequest;
import com.qespe.fiscal_service.core.dto.provider.FiscalProviderConfigResponse;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalProviderConfigEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface FiscalProviderConfigMapper {

    FiscalProviderConfigEntity toEntity(FiscalProviderConfigRequest request);

    void updateEntity(FiscalProviderConfigRequest request, @MappingTarget FiscalProviderConfigEntity entity);

    FiscalProviderConfigResponse toResponse(FiscalProviderConfigEntity entity);
}

