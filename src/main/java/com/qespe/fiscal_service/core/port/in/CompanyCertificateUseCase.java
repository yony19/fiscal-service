package com.qespe.fiscal_service.core.port.in;

import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateRequest;
import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CompanyCertificateUseCase {
    CompanyCertificateResponse create(CompanyCertificateRequest request);
    CompanyCertificateResponse update(UUID id, CompanyCertificateRequest request);
    CompanyCertificateResponse getById(UUID id);
    Page<CompanyCertificateResponse> list(UUID companyId, Pageable pageable);
}
