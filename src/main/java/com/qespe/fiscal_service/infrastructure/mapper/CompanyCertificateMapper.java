package com.qespe.fiscal_service.infrastructure.mapper;

import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateRequest;
import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateResponse;
import com.qespe.fiscal_service.infrastructure.persistence.entity.CompanyCertificateEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CompanyCertificateMapper {

    @Mapping(target = "certificateData", ignore = true)
    @Mapping(target = "privateKeyData", ignore = true)
    CompanyCertificateEntity toEntity(CompanyCertificateRequest request);

    CompanyCertificateResponse toResponse(CompanyCertificateEntity entity);

    @Mapping(target = "certificateData", ignore = true)
    @Mapping(target = "privateKeyData", ignore = true)
    void updateEntity(CompanyCertificateRequest request, @MappingTarget CompanyCertificateEntity entity);
}

