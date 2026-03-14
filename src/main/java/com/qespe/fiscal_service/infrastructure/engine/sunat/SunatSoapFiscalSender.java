package com.qespe.fiscal_service.infrastructure.engine.sunat;

import com.qespe.fiscal_service.core.domain.engine.ProviderContext;
import com.qespe.fiscal_service.core.domain.engine.SendResult;
import com.qespe.fiscal_service.core.domain.engine.SignedArtifactResult;
import com.qespe.fiscal_service.core.domain.engine.StoredArtifactResult;
import com.qespe.fiscal_service.core.domain.enums.FiscalDocumentStatus;
import com.qespe.fiscal_service.core.port.out.FiscalArtifactStoragePort;
import com.qespe.fiscal_service.core.port.out.FiscalSenderPort;
import com.qespe.fiscal_service.infrastructure.engine.sign.SecretValueResolver;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Component
@Primary
public class SunatSoapFiscalSender implements FiscalSenderPort {

    private final SecretValueResolver secretValueResolver;
    private final FiscalArtifactStoragePort artifactStoragePort;

    public SunatSoapFiscalSender(SecretValueResolver secretValueResolver, FiscalArtifactStoragePort artifactStoragePort) {
        this.secretValueResolver = secretValueResolver;
        this.artifactStoragePort = artifactStoragePort;
    }

    @Override
    public SendResult send(FiscalDocumentEntity document, SignedArtifactResult signedArtifactResult, ProviderContext providerContext) {
        validateInputs(signedArtifactResult, providerContext);

        SunatCredentials credentials = resolveCredentials(providerContext);
        byte[] zipBytes = zipSignedXml(document, signedArtifactResult);
        String zipFilename = safeFilename(document.getFullNumber()) + ".zip";
        String soapBody = buildSoapEnvelope(credentials, zipFilename, zipBytes);

        try {
            ResponseEntity<String> response = buildRestTemplate(providerContext.timeoutMs())
                    .postForEntity(providerContext.endpointSubmitUrl(), buildHttpEntity(soapBody), String.class);
            return parseSoapResponse(document, response.getBody());
        } catch (HttpStatusCodeException ex) {
            return parseSoapResponse(document, ex.getResponseBodyAsString());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("SUNAT send failed");
        }
    }

    private void validateInputs(SignedArtifactResult signedArtifactResult, ProviderContext providerContext) {
        if (signedArtifactResult.signedXmlContent() == null || signedArtifactResult.signedXmlContent().isBlank()) {
            throw new BusinessException("Signed XML artifact content is required for send step");
        }
        if (providerContext.endpointSubmitUrl() == null || providerContext.endpointSubmitUrl().isBlank()) {
            throw new BusinessException("Provider submit endpoint is required for SUNAT send");
        }
    }

    private RestTemplate buildRestTemplate(Integer timeoutMs) {
        int timeout = timeoutMs != null && timeoutMs > 0 ? timeoutMs : (int) Duration.ofSeconds(30).toMillis();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    private HttpEntity<String> buildHttpEntity(String soapBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        headers.add(HttpHeaders.ACCEPT, MediaType.TEXT_XML_VALUE);
        headers.add("SOAPAction", "\"\"");
        return new HttpEntity<>(soapBody, headers);
    }

    private SunatCredentials resolveCredentials(ProviderContext providerContext) {
        String rawSecret = secretValueResolver.resolve(providerContext.credentialRef());
        if (rawSecret == null || rawSecret.isBlank()) {
            throw new BusinessException("Provider credentialRef must resolve SUNAT credentials");
        }

        String trimmed = rawSecret.trim();
        if (trimmed.startsWith("{")) {
            return fromJson(trimmed);
        }

        int separator = trimmed.indexOf(':');
        if (separator <= 0 || separator == trimmed.length() - 1) {
            throw new BusinessException("SUNAT credentials must be JSON or username:password");
        }

        return new SunatCredentials(trimmed.substring(0, separator), trimmed.substring(separator + 1));
    }

    private SunatCredentials fromJson(String json) {
        String compact = json.replace("\r", "").replace("\n", "").trim();
        String username = extractJsonValue(compact, "username");
        String password = extractJsonValue(compact, "password");
        if (username == null || username.isBlank() || password == null) {
            throw new BusinessException("SUNAT credential JSON must contain username and password");
        }
        return new SunatCredentials(username, password);
    }

    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyPos = json.indexOf(pattern);
        if (keyPos < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyPos + pattern.length());
        int startQuote = json.indexOf('"', colon + 1);
        int endQuote = json.indexOf('"', startQuote + 1);
        if (colon < 0 || startQuote < 0 || endQuote < 0) {
            return null;
        }
        return json.substring(startQuote + 1, endQuote);
    }

    private byte[] zipSignedXml(FiscalDocumentEntity document, SignedArtifactResult signedArtifactResult) {
        String xmlFilename = safeFilename(document.getFullNumber()) + ".xml";
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
                zos.putNextEntry(new ZipEntry(xmlFilename));
                zos.write(signedArtifactResult.signedXmlContent().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException("Unable to package signed XML for SUNAT send");
        }
    }

    private String buildSoapEnvelope(SunatCredentials credentials, String zipFilename, byte[] zipBytes) {
        String content = Base64.getEncoder().encodeToString(zipBytes);
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:ser="http://service.sunat.gob.pe"
                                  xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                  <soapenv:Header>
                    <wsse:Security>
                      <wsse:UsernameToken>
                        <wsse:Username>%s</wsse:Username>
                        <wsse:Password>%s</wsse:Password>
                      </wsse:UsernameToken>
                    </wsse:Security>
                  </soapenv:Header>
                  <soapenv:Body>
                    <ser:sendBill>
                      <fileName>%s</fileName>
                      <contentFile>%s</contentFile>
                    </ser:sendBill>
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(
                escapeXml(credentials.username()),
                escapeXml(credentials.password()),
                escapeXml(zipFilename),
                content
        );
    }

    private SendResult parseSoapResponse(FiscalDocumentEntity document, String body) {
        if (body == null || body.isBlank()) {
            return new SendResult(FiscalDocumentStatus.ERROR, "EMPTY_RESPONSE", "SUNAT returned empty response", null, null);
        }

        try {
            Document soapDoc = parseXml(body);
            Element fault = firstElementByLocalName(soapDoc.getDocumentElement(), "Fault");
            if (fault != null) {
                String faultCode = childText(fault, "faultcode");
                String faultMessage = childText(fault, "faultstring");
                return new SendResult(
                        FiscalDocumentStatus.REJECTED,
                        blankToDefault(faultCode, "SOAP_FAULT"),
                        blankToDefault(faultMessage, "SUNAT SOAP fault"),
                        null,
                        null
                );
            }

            String applicationResponse = textByLocalName(soapDoc.getDocumentElement(), "applicationResponse");
            if (applicationResponse == null || applicationResponse.isBlank()) {
                return new SendResult(FiscalDocumentStatus.ERROR, "INVALID_RESPONSE", "SUNAT response did not include applicationResponse", null, null);
            }

            byte[] cdrZipBytes = Base64.getDecoder().decode(applicationResponse.trim());
            StoredArtifactResult storedCdr = artifactStoragePort.storeCdr(document, cdrZipBytes);
            CdrInfo cdrInfo = extractCdrInfo(cdrZipBytes);
            FiscalDocumentStatus status = "0".equals(cdrInfo.responseCode()) ? FiscalDocumentStatus.ACCEPTED : FiscalDocumentStatus.REJECTED;

            return new SendResult(
                    status,
                    cdrInfo.responseCode(),
                    cdrInfo.description(),
                    null,
                    storedCdr.path()
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            return new SendResult(FiscalDocumentStatus.ERROR, "PARSE_ERROR", "Unable to parse SUNAT response", null, null);
        }
    }

    private CdrInfo extractCdrInfo(byte[] cdrZipBytes) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(cdrZipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".xml")) {
                    byte[] xmlBytes = zis.readAllBytes();
                    Document cdrDoc = parseXml(new String(xmlBytes, StandardCharsets.UTF_8));
                    String responseCode = textByLocalName(cdrDoc.getDocumentElement(), "ResponseCode");
                    String description = textByLocalName(cdrDoc.getDocumentElement(), "Description");
                    return new CdrInfo(
                            blankToDefault(responseCode, "UNKNOWN"),
                            blankToDefault(description, "SUNAT returned CDR without description")
                    );
                }
            }
        } catch (Exception ex) {
            throw new BusinessException("Unable to parse CDR response");
        }
        throw new BusinessException("CDR zip did not contain XML response");
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            return factory.newDocumentBuilder().parse(is);
        }
    }

    private String textByLocalName(Element root, String localName) {
        Element element = firstElementByLocalName(root, localName);
        return element == null ? null : element.getTextContent();
    }

    private Element firstElementByLocalName(Element root, String localName) {
        if (matches(root, localName)) {
            return root;
        }
        for (int i = 0; i < root.getChildNodes().getLength(); i++) {
            Node node = root.getChildNodes().item(i);
            if (node instanceof Element child) {
                Element found = firstElementByLocalName(child, localName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean matches(Element element, String localName) {
        return localName.equals(element.getLocalName()) || localName.equals(element.getNodeName());
    }

    private String childText(Element parent, String localName) {
        for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
            Node node = parent.getChildNodes().item(i);
            if (node instanceof Element child && matches(child, localName)) {
                return child.getTextContent();
            }
        }
        return null;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String safeFilename(String value) {
        if (value == null || value.isBlank()) {
            return "document";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private record SunatCredentials(String username, String password) {
    }

    private record CdrInfo(String responseCode, String description) {
    }
}
