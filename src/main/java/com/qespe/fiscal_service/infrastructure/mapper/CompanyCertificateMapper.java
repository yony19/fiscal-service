package com.qespe.fiscal_service.infrastructure.mapper;

import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateRequest;
import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateResponse;
import com.qespe.fiscal_service.infrastructure.persistence.entity.CompanyCertificateEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

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

    // NullValuePropertyMappingStrategy.IGNORE: un valor null en el request NO
    // pisa el valor ya guardado en la entidad. Sin esto, el formulario
    // "amigable" de metadata (alias/proveedor/estado/default — el único que
    // expone el frontend) mandaba validFrom/validTo/fingerprintSha256/
    // storageMode/certificatePath/privateKeyPath/secretRef/passwordSecretRef
    // en null porque no los conoce, y updateEntity los borraba en cada
    // edición aunque uploadPfx ya los hubiera poblado correctamente
    // (hallazgo 2026-07-23: certificado subido con éxito pero "no vigente"
    // tras editar el alias/estado). Con IGNORE, esos campos solo cambian si
    // el caller los manda explícitamente (ej. un futuro admin-override real).
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "certificateData", ignore = true)
    @Mapping(target = "privateKeyData", ignore = true)
    @Mapping(target = "passwordEncrypted", ignore = true)
    @Mapping(target = "passwordIv", ignore = true)
    @Mapping(target = "uploadFilename", ignore = true)
    @Mapping(target = "uploadedAt", ignore = true)
    void updateEntity(CompanyCertificateRequest request, @MappingTarget CompanyCertificateEntity entity);

    /** True when the .pfx bytes are stored inline (post-upload). */
    @Named("hasBytes")
    default boolean hasBytes(byte[] data) {
        return data != null && data.length > 0;
    }
}

