package com.qespe.fiscal_service.infrastructure.mapper;

import com.qespe.fiscal_service.core.dto.document.FiscalDocumentLineResponse;
import com.qespe.fiscal_service.core.dto.document.FiscalDocumentReserveResponse;
import com.qespe.fiscal_service.core.dto.document.FiscalDocumentResponse;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentLineEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface FiscalDocumentMapper {

    FiscalDocumentReserveResponse toReserveResponse(FiscalDocumentEntity entity);

    FiscalDocumentResponse toResponse(FiscalDocumentEntity entity);

    FiscalDocumentLineResponse toLineResponse(FiscalDocumentLineEntity entity);

    List<FiscalDocumentLineResponse> toLineResponses(List<FiscalDocumentLineEntity> entities);
}

