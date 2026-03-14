package com.qespe.fiscal_service.core.usecase.engine;

import com.qespe.fiscal_service.core.domain.engine.*;
import com.qespe.fiscal_service.core.domain.enums.FiscalDocumentStatus;
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

        if (document.getStatus() != FiscalDocumentStatus.RESERVED) {
            appendEvent(document, "PROCESSING_SKIPPED", "Document is already in non-processable interim state", Map.of("status", document.getStatus().name()));
            return toResponse(document);
        }

        appendEvent(document, "PROCESSING_STARTED", "Fiscal engine processing started", Map.of());

        try {
            consistencyValidator.validateForProcessing(document);
            transition(document, FiscalDocumentStatus.PENDING_XML, "Moved to PENDING_XML", "STATUS_CHANGED", Map.of("to", "PENDING_XML"));

            EmitterContext emitterContext = emitterResolver.resolve(document);
            appendEvent(document, "EMITTER_RESOLVED", "Emitter config resolved", Map.of("emitterConfigId", emitterContext.emitterConfigId()));

            ProviderContext providerContext = providerResolver.resolve(document);
            document.setProviderCode(providerContext.providerCode());
            appendEvent(document, "PROVIDER_RESOLVED", "Provider config resolved", Map.of("providerConfigId", providerContext.providerConfigId(), "providerCode", providerContext.providerCode()));

            CertificateContext certificateContext = certificateResolver.resolve(document, providerContext.providerCode());
            java.util.Map<String, Object> certPayload = new java.util.HashMap<>();
            certPayload.put("certificateId", certificateContext.certificateId());
            certPayload.put("providerCode", certificateContext.providerCode());
            certPayload.put("alias", certificateContext.alias());
            appendEvent(document, "CERTIFICATE_RESOLVED", "Certificate resolved", certPayload);

            XmlBuildResult xmlResult = xmlBuilder.build(document, emitterContext);
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

            SignedArtifactResult signed;
            try {
                signed = signer.sign(document, xmlResult, certificateContext);
            } catch (BusinessException ex) {
                appendEvent(document, "SIGNING_FAILED", "XML signing failed", Map.of("errorCode", "SIGNING_ERROR"));
                throw ex;
            }
            document.setSignedXmlPath(signed.signedXmlPath());
            document.setSignedXmlHash(signed.signedXmlHash());
            transition(document, FiscalDocumentStatus.SIGNED, "XML artifact signed", "XML_SIGNED", Map.of(
                    "signatureAlgorithm", signed.signatureAlgorithm(),
                    "signatureReference", signed.signatureReference(),
                    "signedXmlPath", signed.signedXmlPath(),
                    "signedXmlHash", signed.signedXmlHash()
            ));

            transition(document, FiscalDocumentStatus.QUEUED_FOR_SEND, "Document queued for send", "STATUS_CHANGED", Map.of("to", "QUEUED_FOR_SEND"));

            document.setSendAttemptCount((document.getSendAttemptCount() == null ? 0 : document.getSendAttemptCount()) + 1);
            transition(document, FiscalDocumentStatus.SENT, "Send attempted", "SEND_ATTEMPTED", Map.of("attempt", document.getSendAttemptCount()));
            document.setSentAt(Instant.now());

            SendResult sendResult = sender.send(document, signed, providerContext);

            if (sendResult.accepted()) {
                transition(document, FiscalDocumentStatus.ACCEPTED, "Authority send accepted", "SEND_ACCEPTED", Map.of("authorityCode", sendResult.authorityStatusCode()));
                document.setAuthorityStatusCode(sendResult.authorityStatusCode());
                document.setAuthorityStatusMessage(sendResult.authorityStatusMessage());
                document.setAuthorityTicket(sendResult.authorityTicket());
                document.setCdrPath(sendResult.cdrPath());
                document.setAcceptedAt(Instant.now());
                document.setErrorCode(null);
                document.setErrorMessage(null);
            } else if (sendResult.rejected()) {
                transition(document, FiscalDocumentStatus.REJECTED, "Authority send rejected", "SEND_REJECTED", Map.of("authorityCode", sendResult.authorityStatusCode()));
                document.setAuthorityStatusCode(sendResult.authorityStatusCode());
                document.setAuthorityStatusMessage(sendResult.authorityStatusMessage());
                document.setAuthorityTicket(sendResult.authorityTicket());
                document.setCdrPath(sendResult.cdrPath());
                document.setErrorCode("SUNAT_REJECTED");
                document.setErrorMessage(sendResult.authorityStatusMessage());
            } else {
                transition(document, FiscalDocumentStatus.ERROR, "Authority send failed", "SEND_FAILED", Map.of("authorityCode", sendResult.authorityStatusCode()));
                document.setAuthorityStatusCode(sendResult.authorityStatusCode());
                document.setAuthorityStatusMessage(sendResult.authorityStatusMessage());
                document.setAuthorityTicket(sendResult.authorityTicket());
                document.setCdrPath(sendResult.cdrPath());
                document.setErrorCode("SEND_FAILED");
                document.setErrorMessage(sendResult.authorityStatusMessage());
            }

            documentRepository.save(document);
            return toResponse(document);

        } catch (FiscalValidationException ex) {
            appendEvent(document, "XML_VALIDATION_FAILED", "Fiscal payload validation failed", Map.of("errorCode", "FISCAL_VALIDATION_ERROR"));
            markError(document, "PROCESSING_ERROR", ex.getMessage(), "FISCAL_VALIDATION_ERROR");
            return toResponse(document);
        } catch (EmitterValidationException ex) {
            appendEvent(document, "EMITTER_VALIDATION_FAILED", "Emitter validation failed", Map.of("errorCode", "EMITTER_VALIDATION_ERROR"));
            markError(document, "PROCESSING_ERROR", ex.getMessage(), "EMITTER_VALIDATION_ERROR");
            return toResponse(document);
        } catch (BusinessException ex) {
            markError(document, "PROCESSING_ERROR", ex.getMessage(), "BUSINESS_ERROR");
            return toResponse(document);
        } catch (Exception ex) {
            markError(document, "PROCESSING_ERROR", "Unexpected processing failure", "UNEXPECTED_ERROR");
            return toResponse(document);
        }
    }

    private void transition(FiscalDocumentEntity document, FiscalDocumentStatus target, String message, String eventType, Map<String, Object> payload) {
        FiscalDocumentStateMachine.assertTransition(document.getStatus(), target);
        document.setStatus(target);
        documentRepository.save(document);
        appendEvent(document, eventType, message, payload);
    }

    private void markError(FiscalDocumentEntity document, String eventType, String message, String code) {
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
            documentRepository.save(document);
            appendEvent(document, eventType, message, Map.of("errorCode", code));
        } catch (Exception ignored) {
            // avoid leaking internals during error handling
        }
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
                document.getCdrPath(),
                document.getSendAttemptCount(),
                Instant.now()
        );
    }
}

