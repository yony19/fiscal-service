package com.qespe.fiscal_service.core.usecase.engine;

import com.qespe.fiscal_service.core.domain.engine.*;
import com.qespe.fiscal_service.core.domain.enums.FiscalDocumentStatus;
import com.qespe.fiscal_service.core.domain.enums.FiscalProcessingStage;
import com.qespe.fiscal_service.core.dto.engine.FiscalDocumentProcessResponse;
import com.qespe.fiscal_service.core.port.in.FiscalDocumentProcessingUseCase;
import com.qespe.fiscal_service.core.port.out.FiscalDocumentRepositoryPort;
import com.qespe.fiscal_service.core.port.out.FiscalEventRepositoryPort;
import com.qespe.fiscal_service.core.port.out.FiscalSenderPort;
import com.qespe.fiscal_service.core.port.out.FiscalSignerPort;
import com.qespe.fiscal_service.core.port.out.FiscalXmlBuilderPort;
import com.qespe.fiscal_service.core.validation.FiscalDocumentConsistencyValidator;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalEventEntity;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import com.qespe.fiscal_service.shared.exception.EmitterValidationException;
import com.qespe.fiscal_service.shared.exception.FiscalValidationException;
import com.qespe.fiscal_service.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FiscalDocumentProcessingService implements FiscalDocumentProcessingUseCase {

    private final FiscalDocumentRepositoryPort documentRepository;
    private final FiscalEventRepositoryPort eventRepository;
    private final EmitterResolutionService emitterResolver;
    private final ProviderResolutionService providerResolver;
    private final CertificateResolutionService certificateResolver;
    private final FiscalDocumentConsistencyValidator consistencyValidator;
    private final FiscalXmlBuilderPort xmlBuilder;
    private final FiscalSignerPort signer;
    private final FiscalSenderPort sender;

    @Override
    @Transactional
    public FiscalDocumentProcessResponse process(UUID fiscalDocumentId) {
        FiscalDocumentEntity document = documentRepository.findWithLinesById(fiscalDocumentId)
                .orElseThrow(() -> new NotFoundException("Fiscal document not found: " + fiscalDocumentId));

        if (FiscalDocumentStateMachine.isTerminalOrAdvanced(document.getStatus())) {
            appendEvent(document, "PROCESSING_SKIPPED", "Document is already in terminal/advanced state", Map.of("status", document.getStatus().name()));
            return toResponse(document);
        }

        if (!canProcess(document)) {
            appendEvent(document, "PROCESSING_SKIPPED", "Document is already in non-processable interim state", buildSkipPayload(document));
            return toResponse(document);
        }

        appendEvent(document, "PROCESSING_STARTED", "Fiscal engine processing started", Map.of());

        try {
            ProcessingCheckpoint checkpoint = initializeProcessing(document);

            ProviderContext providerContext = providerResolver.resolve(document);
            document.setProviderCode(providerContext.providerCode());
            appendEvent(document, "PROVIDER_RESOLVED", "Provider config resolved", Map.of(
                    "providerConfigId", providerContext.providerConfigId(),
                    "providerCode", providerContext.providerCode()
            ));

            SignedArtifactResult signedArtifact = checkpoint.signedArtifact();
            if (signedArtifact == null) {
                CertificateContext certificateContext = certificateResolver.resolve(document, providerContext.providerCode());
                Map<String, Object> certPayload = new java.util.HashMap<>();
                certPayload.put("certificateId", certificateContext.certificateId());
                certPayload.put("providerCode", certificateContext.providerCode());
                certPayload.put("alias", certificateContext.alias());
                appendEvent(document, "CERTIFICATE_RESOLVED", "Certificate resolved", certPayload);

                XmlBuildResult xmlResult = checkpoint.xmlBuildResult();
                if (xmlResult == null) {
                    xmlResult = xmlBuilder.build(document, checkpoint.emitterContext());
                    document.setXmlPath(xmlResult.xmlPath());
                    document.setXmlHash(xmlResult.xmlHash());
                    document.setQrData(xmlResult.qrData());
                    transition(
                            document,
                            FiscalDocumentStatus.XML_GENERATED,
                            "XML artifact generated",
                            "XML_GENERATED",
                            Map.of("xmlPath", xmlResult.xmlPath(), "xmlHash", xmlResult.xmlHash())
                    );
                }

                try {
                    signedArtifact = signer.sign(document, xmlResult, certificateContext);
                } catch (BusinessException ex) {
                    appendEvent(document, "SIGNING_FAILED", "XML signing failed", Map.of("errorCode", "SIGNING_ERROR"));
                    throw ex;
                }

                document.setSignedXmlPath(signedArtifact.signedXmlPath());
                document.setSignedXmlHash(signedArtifact.signedXmlHash());
                transition(document, FiscalDocumentStatus.SIGNED, "XML artifact signed", "XML_SIGNED", Map.of(
                        "signatureAlgorithm", signedArtifact.signatureAlgorithm(),
                        "signatureReference", signedArtifact.signatureReference(),
                        "signedXmlPath", signedArtifact.signedXmlPath(),
                        "signedXmlHash", signedArtifact.signedXmlHash()
                ));
            }

            clearRetryMetadata(document);
            transition(document, FiscalDocumentStatus.QUEUED_FOR_SEND, "Document queued for send", "STATUS_CHANGED", Map.of("to", "QUEUED_FOR_SEND"));

            document.setSendAttemptCount((document.getSendAttemptCount() == null ? 0 : document.getSendAttemptCount()) + 1);
            transition(document, FiscalDocumentStatus.SENT, "Send attempted", "SEND_ATTEMPTED", Map.of("attempt", document.getSendAttemptCount()));
            document.setSentAt(Instant.now());

            SendResult sendResult = sender.send(document, signedArtifact, providerContext);

            if (sendResult.accepted()) {
                transition(document, FiscalDocumentStatus.ACCEPTED, "Authority send accepted", "SEND_ACCEPTED", Map.of("authorityCode", sendResult.authorityStatusCode()));
                document.setAuthorityStatusCode(sendResult.authorityStatusCode());
                document.setAuthorityStatusMessage(sendResult.authorityStatusMessage());
                document.setAuthorityTicket(sendResult.authorityTicket());
                document.setZipPath(sendResult.zipPath());
                document.setZipHash(sendResult.zipHash());
                document.setResponsePath(sendResult.responsePath());
                document.setResponseHash(sendResult.responseHash());
                document.setCdrPath(sendResult.cdrPath());
                document.setCdrHash(sendResult.cdrHash());
                document.setAcceptedAt(Instant.now());
                clearRetryMetadata(document);
            } else if (sendResult.rejected()) {
                transition(document, FiscalDocumentStatus.REJECTED, "Authority send rejected", "SEND_REJECTED", Map.of("authorityCode", sendResult.authorityStatusCode()));
                document.setAuthorityStatusCode(sendResult.authorityStatusCode());
                document.setAuthorityStatusMessage(sendResult.authorityStatusMessage());
                document.setAuthorityTicket(sendResult.authorityTicket());
                document.setZipPath(sendResult.zipPath());
                document.setZipHash(sendResult.zipHash());
                document.setResponsePath(sendResult.responsePath());
                document.setResponseHash(sendResult.responseHash());
                document.setCdrPath(sendResult.cdrPath());
                document.setCdrHash(sendResult.cdrHash());
                document.setRejectedAt(Instant.now());
                document.setErrorCode("SUNAT_REJECTED");
                document.setErrorMessage(sendResult.authorityStatusMessage());
                document.setRetryableError(false);
                document.setLastFailedStage(FiscalProcessingStage.SEND);
                document.setLastErrorAt(Instant.now());
            } else {
                transition(document, FiscalDocumentStatus.ERROR, "Authority send failed", "SEND_FAILED", Map.of("authorityCode", sendResult.authorityStatusCode()));
                document.setAuthorityStatusCode(sendResult.authorityStatusCode());
                document.setAuthorityStatusMessage(sendResult.authorityStatusMessage());
                document.setAuthorityTicket(sendResult.authorityTicket());
                document.setZipPath(sendResult.zipPath());
                document.setZipHash(sendResult.zipHash());
                document.setResponsePath(sendResult.responsePath());
                document.setResponseHash(sendResult.responseHash());
                document.setCdrPath(sendResult.cdrPath());
                document.setCdrHash(sendResult.cdrHash());
                document.setErrorCode(sendResult.authorityStatusCode() == null || sendResult.authorityStatusCode().isBlank() ? "SEND_FAILED" : sendResult.authorityStatusCode());
                document.setErrorMessage(sendResult.authorityStatusMessage());
                document.setRetryableError(sendResult.retryableError());
                document.setLastFailedStage(FiscalProcessingStage.SEND);
                document.setLastErrorAt(Instant.now());
                applyRetryPolicy(document, providerContext, sendResult.retryableError());
            }

            documentRepository.save(document);
            return toResponse(document);

        } catch (FiscalValidationException ex) {
            appendEvent(document, "XML_VALIDATION_FAILED", "Fiscal payload validation failed", Map.of("errorCode", "FISCAL_VALIDATION_ERROR"));
            markError(document, "PROCESSING_ERROR", ex.getMessage(), "FISCAL_VALIDATION_ERROR", FiscalProcessingStage.VALIDATION, false);
            return toResponse(document);
        } catch (EmitterValidationException ex) {
            appendEvent(document, "EMITTER_VALIDATION_FAILED", "Emitter validation failed", Map.of("errorCode", "EMITTER_VALIDATION_ERROR"));
            markError(document, "PROCESSING_ERROR", ex.getMessage(), "EMITTER_VALIDATION_ERROR", FiscalProcessingStage.EMITTER_RESOLUTION, false);
            return toResponse(document);
        } catch (BusinessException ex) {
            markError(document, "PROCESSING_ERROR", ex.getMessage(), "BUSINESS_ERROR", inferFailedStage(document), false);
            return toResponse(document);
        } catch (Exception ex) {
            markError(document, "PROCESSING_ERROR", "Unexpected processing failure", "UNEXPECTED_ERROR", inferFailedStage(document), false);
            return toResponse(document);
        }
    }

    @Override
    @Transactional
    public FiscalDocumentProcessResponse retry(UUID fiscalDocumentId) {
        FiscalDocumentEntity document = documentRepository.findWithLinesById(fiscalDocumentId)
                .orElseThrow(() -> new NotFoundException("Fiscal document not found: " + fiscalDocumentId));

        boolean explicitRetryCandidate = document.getStatus() == FiscalDocumentStatus.ERROR
                || document.getStatus() == FiscalDocumentStatus.SIGNED
                || document.getStatus() == FiscalDocumentStatus.XML_GENERATED;

        if (!explicitRetryCandidate) {
            throw new BusinessException("Retry is only allowed for retry candidates in XML_GENERATED, SIGNED or ERROR status");
        }

        appendEvent(document, "RETRY_REQUESTED", "Explicit retry requested", Map.of(
                "status", document.getStatus().name(),
                "retryableError", document.getRetryableError(),
                "lastFailedStage", document.getLastFailedStage() == null ? null : document.getLastFailedStage().name(),
                "nextRetryAt", document.getNextRetryAt()
        ));

        return process(fiscalDocumentId);
    }

    private boolean canProcess(FiscalDocumentEntity document) {
        if (document.getStatus() == FiscalDocumentStatus.RESERVED
                || document.getStatus() == FiscalDocumentStatus.XML_GENERATED
                || document.getStatus() == FiscalDocumentStatus.SIGNED) {
            return true;
        }
        return document.getStatus() == FiscalDocumentStatus.ERROR
                && Boolean.TRUE.equals(document.getRetryableError())
                && document.getLastFailedStage() == FiscalProcessingStage.SEND
                && isRetryWindowOpen(document)
                && document.getSignedXmlPath() != null
                && !document.getSignedXmlPath().isBlank();
    }

    private ProcessingCheckpoint initializeProcessing(FiscalDocumentEntity document) {
        if (document.getStatus() == FiscalDocumentStatus.RESERVED) {
            consistencyValidator.validateForProcessing(document);
            transition(document, FiscalDocumentStatus.PENDING_XML, "Moved to PENDING_XML", "STATUS_CHANGED", Map.of("to", "PENDING_XML"));
            EmitterContext emitterContext = emitterResolver.resolve(document);
            appendEvent(document, "EMITTER_RESOLVED", "Emitter config resolved", Map.of("emitterConfigId", emitterContext.emitterConfigId()));
            return new ProcessingCheckpoint(emitterContext, null, null);
        }

        if (document.getStatus() == FiscalDocumentStatus.XML_GENERATED) {
            return new ProcessingCheckpoint(null, loadExistingXml(document), null);
        }

        if (document.getStatus() == FiscalDocumentStatus.SIGNED) {
            return new ProcessingCheckpoint(null, null, loadExistingSignedArtifact(document));
        }

        if (document.getStatus() == FiscalDocumentStatus.ERROR
                && Boolean.TRUE.equals(document.getRetryableError())
                && document.getLastFailedStage() == FiscalProcessingStage.SEND) {
            document.setStatus(FiscalDocumentStatus.SIGNED);
            documentRepository.save(document);
            appendEvent(document, "PROCESSING_RESUMED", "Retryable send resumed from SIGNED artifact", Map.of("fromStatus", "ERROR"));
            return new ProcessingCheckpoint(null, null, loadExistingSignedArtifact(document));
        }

        throw new BusinessException("Document is not in a processable state");
    }

    private XmlBuildResult loadExistingXml(FiscalDocumentEntity document) {
        if (document.getXmlPath() == null || document.getXmlPath().isBlank()) {
            throw new BusinessException("Existing XML artifact path is required to resume signing");
        }
        String xmlContent = readFile(document.getXmlPath(), "XML artifact could not be loaded for retry");
        return new XmlBuildResult(xmlContent, document.getXmlPath(), document.getXmlHash(), document.getQrData());
    }

    private SignedArtifactResult loadExistingSignedArtifact(FiscalDocumentEntity document) {
        if (document.getSignedXmlPath() == null || document.getSignedXmlPath().isBlank()) {
            throw new BusinessException("Signed XML artifact path is required to resume send");
        }
        String signedXmlContent = readFile(document.getSignedXmlPath(), "Signed XML artifact could not be loaded for retry");
        return new SignedArtifactResult(
                signedXmlContent,
                document.getSignedXmlPath(),
                document.getSignedXmlHash(),
                "XMLDSIG-" + document.getId(),
                null
        );
    }

    private String readFile(String path, String errorMessage) {
        try {
            return Files.readString(Path.of(path));
        } catch (Exception ex) {
            throw new BusinessException(errorMessage);
        }
    }

    private void transition(FiscalDocumentEntity document, FiscalDocumentStatus target, String message, String eventType, Map<String, Object> payload) {
        FiscalDocumentStateMachine.assertTransition(document.getStatus(), target);
        document.setStatus(target);
        documentRepository.save(document);
        appendEvent(document, eventType, message, payload);
    }

    private void markError(FiscalDocumentEntity document, String eventType, String message, String code, FiscalProcessingStage failedStage, boolean retryable) {
        try {
            if (document.getStatus() != FiscalDocumentStatus.ERROR) {
                if (document.getStatus() == FiscalDocumentStatus.RESERVED
                        || document.getStatus() == FiscalDocumentStatus.PENDING_XML
                        || document.getStatus() == FiscalDocumentStatus.XML_GENERATED
                        || document.getStatus() == FiscalDocumentStatus.SIGNED
                        || document.getStatus() == FiscalDocumentStatus.QUEUED_FOR_SEND
                        || document.getStatus() == FiscalDocumentStatus.SENT) {
                    document.setStatus(FiscalDocumentStatus.ERROR);
                }
            }
            document.setErrorCode(code);
            document.setErrorMessage(message);
            document.setRetryableError(retryable);
            document.setLastFailedStage(failedStage);
            document.setLastErrorAt(Instant.now());
            documentRepository.save(document);
            appendEvent(document, eventType, message, Map.of(
                    "errorCode", code,
                    "retryable", retryable,
                    "failedStage", failedStage == null ? FiscalProcessingStage.UNKNOWN.name() : failedStage.name()
            ));
        } catch (Exception ignored) {
            // avoid leaking internals during error handling
        }
    }

    private FiscalProcessingStage inferFailedStage(FiscalDocumentEntity document) {
        if (document.getStatus() == FiscalDocumentStatus.RESERVED || document.getStatus() == FiscalDocumentStatus.PENDING_XML) {
            return FiscalProcessingStage.VALIDATION;
        }
        if (document.getStatus() == FiscalDocumentStatus.XML_GENERATED) {
            return FiscalProcessingStage.SIGN;
        }
        if (document.getStatus() == FiscalDocumentStatus.SIGNED
                || document.getStatus() == FiscalDocumentStatus.QUEUED_FOR_SEND
                || document.getStatus() == FiscalDocumentStatus.SENT) {
            return FiscalProcessingStage.SEND;
        }
        return FiscalProcessingStage.UNKNOWN;
    }

    private void clearRetryMetadata(FiscalDocumentEntity document) {
        document.setErrorCode(null);
        document.setErrorMessage(null);
        document.setRetryableError(false);
        document.setLastFailedStage(null);
        document.setLastErrorAt(null);
        document.setRetryCount(0);
        document.setNextRetryAt(null);
    }

    private void applyRetryPolicy(FiscalDocumentEntity document, ProviderContext providerContext, boolean retryable) {
        if (!retryable) {
            document.setNextRetryAt(null);
            return;
        }

        int currentRetryCount = document.getRetryCount() == null ? 0 : document.getRetryCount();
        int nextRetryCount = currentRetryCount + 1;
        document.setRetryCount(nextRetryCount);

        Integer maxRetries = providerContext.maxRetries();
        if (maxRetries != null && maxRetries >= 0 && nextRetryCount > maxRetries) {
            document.setRetryableError(false);
            document.setNextRetryAt(null);
            document.setErrorCode("SEND_RETRY_EXHAUSTED");
            return;
        }

        int backoffMs = providerContext.retryBackoffMs() != null && providerContext.retryBackoffMs() > 0
                ? providerContext.retryBackoffMs()
                : 30000;
        document.setNextRetryAt(Instant.now().plusMillis(backoffMs));
    }

    private boolean isRetryWindowOpen(FiscalDocumentEntity document) {
        return document.getNextRetryAt() == null || !Instant.now().isBefore(document.getNextRetryAt());
    }

    private void appendEvent(FiscalDocumentEntity document, String eventType, String message, Map<String, Object> payload) {
        FiscalEventEntity event = new FiscalEventEntity();
        event.setFiscalDocument(document);
        event.setEventType(eventType);
        event.setMessage(message);
        event.setPayload(payload == null ? Map.of() : payload);
        eventRepository.save(event);
    }

    private FiscalDocumentProcessResponse toResponse(FiscalDocumentEntity document) {
        return new FiscalDocumentProcessResponse(
                document.getId(),
                document.getStatus().name(),
                document.getAuthorityStatusCode(),
                document.getAuthorityStatusMessage(),
                document.getXmlPath(),
                document.getSignedXmlPath(),
                document.getZipPath(),
                document.getResponsePath(),
                document.getCdrPath(),
                document.getSendAttemptCount(),
                document.getRetryableError(),
                document.getLastFailedStage() == null ? null : document.getLastFailedStage().name(),
                document.getRetryCount(),
                document.getNextRetryAt(),
                Instant.now()
        );
    }

    private Map<String, Object> buildSkipPayload(FiscalDocumentEntity document) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("status", document.getStatus().name());
        payload.put("retryableError", document.getRetryableError());
        payload.put("lastFailedStage", document.getLastFailedStage() == null ? null : document.getLastFailedStage().name());
        payload.put("nextRetryAt", document.getNextRetryAt());
        return payload;
    }

    private record ProcessingCheckpoint(
            EmitterContext emitterContext,
            XmlBuildResult xmlBuildResult,
            SignedArtifactResult signedArtifact
    ) {
    }
}
