package com.qespe.fiscal_service.infrastructure.mapper;

import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateRequest;
import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateResponse;
import com.qespe.fiscal_service.infrastructure.persistence.entity.CompanyCertificateEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface CompanyCertificateMapper {

    // Binary + crypto fields are NEVER set from the JSON payload — only the
    // multipart upload pipeline (CertificateUploadService) writes them.
    // Same for the audit/extracted fields populated on upload.
    @Mapping(target = "certificateData", ignore = true)
    @Mapping(target = "privateKeyData", ignore = true)
    @Mapping(target = "passwordEncrypted", ignore = true)
    @Mapping(target = "passwordIv", ignore = true)
    @Mapping(target = "uploadFilename", ignore = true)
    @Mapping(target = "uploadedAt", ignore = true)
    CompanyCertificateEntity toEntity(CompanyCertificateRequest request);

    @Mapping(target = "hasFile", source = "certificateData", qualifiedByName = "hasBytes")
    CompanyCertificateResponse toResponse(CompanyCertificateEntity entity);

    @Mapping(target = "certificateData", ignore = true)
    @Mapping(target = "privateKeyData", ignore = true)
    @Mapping(target = "passwordEncrypted", ignore = true)
    @Mapping(target = "passwordIv", ignore = true)
    @Mapping(target = "uploadFilename", ignore = true)
    @Mapping(target = "uploadedAt", ignore = true)
    // valid_from / valid_to are also extracted from the cert; keep them
    // editable from the JSON path so admins can override if a cert is
    // missing the dates (rare but possible for self-signed test certs).
    void updateEntity(CompanyCertificateRequest request, @MappingTarget CompanyCertificateEntity entity);

    /** True when the .pfx bytes are stored inline (post-upload). */
    @Named("hasBytes")
    default boolean hasBytes(byte[] data) {
        return data != null && data.length > 0;
    }
}

