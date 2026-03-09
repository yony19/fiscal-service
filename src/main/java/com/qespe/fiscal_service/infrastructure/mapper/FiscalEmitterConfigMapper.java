package com.qespe.fiscal_service.infrastructure.mapper;

import com.qespe.fiscal_service.core.dto.emitter.FiscalEmitterConfigRequest;
import com.qespe.fiscal_service.core.dto.emitter.FiscalEmitterConfigResponse;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalEmitterConfigEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface FiscalEmitterConfigMapper {

    FiscalEmitterConfigEntity toEntity(FiscalEmitterConfigRequest request);

    void updateEntity(FiscalEmitterConfigRequest request, @MappingTarget FiscalEmitterConfigEntity entity);

    FiscalEmitterConfigResponse toResponse(FiscalEmitterConfigEntity entity);
}
