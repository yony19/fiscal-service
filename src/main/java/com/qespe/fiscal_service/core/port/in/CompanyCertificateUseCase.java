package com.qespe.fiscal_service.core.port.in;

import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateRequest;
import com.qespe.fiscal_service.core.dto.certificate.CompanyCertificateResponse;

import java.util.List;
import java.util.UUID;

public interface CompanyCertificateUseCase {
    CompanyCertificateResponse create(CompanyCertificateRequest request);
    CompanyCertificateResponse update(UUID id, CompanyCertificateRequest request);
    CompanyCertificateResponse getById(UUID id);
    List<CompanyCertificateResponse> list(UUID companyId);
}

