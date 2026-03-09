package com.qespe.fiscal_service.infrastructure.mapper;

import com.qespe.fiscal_service.core.dto.event.FiscalEventResponse;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalEventEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface FiscalEventMapper {

    FiscalEventResponse toResponse(FiscalEventEntity entity);

    List<FiscalEventResponse> toResponses(List<FiscalEventEntity> entities);
}

