package com.qespe.fiscal_service.infrastructure.mapper;

import com.qespe.fiscal_service.core.dto.series.FiscalSeriesRequest;
import com.qespe.fiscal_service.core.dto.series.FiscalSeriesResponse;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalSeriesEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface FiscalSeriesMapper {

    FiscalSeriesResponse toResponse(FiscalSeriesEntity entity);

    FiscalSeriesEntity toEntity(FiscalSeriesRequest request);

    void updateEntity(FiscalSeriesRequest request, @MappingTarget FiscalSeriesEntity entity);
}

