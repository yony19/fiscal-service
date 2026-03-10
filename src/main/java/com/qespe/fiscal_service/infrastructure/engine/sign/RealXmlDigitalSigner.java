package com.qespe.fiscal_service.infrastructure.engine.sign;

import com.qespe.fiscal_service.core.domain.engine.CertificateContext;
import com.qespe.fiscal_service.core.domain.engine.SignedArtifactResult;
import com.qespe.fiscal_service.core.domain.engine.StoredArtifactResult;
import com.qespe.fiscal_service.core.domain.engine.XmlBuildResult;
import com.qespe.fiscal_service.core.port.out.FiscalArtifactStoragePort;
import com.qespe.fiscal_service.core.port.out.FiscalSignerPort;
import com.qespe.fiscal_service.infrastructure.persistence.entity.FiscalDocumentEntity;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Collections;

@Component
@Primary
public class RealXmlDigitalSigner implements FiscalSignerPort {

    private final CertificateMaterialLoader certificateMaterialLoader;
    private final FiscalArtifactStoragePort artifactStoragePort;

    public RealXmlDigitalSigner(CertificateMaterialLoader certificateMaterialLoader, FiscalArtifactStoragePort artifactStoragePort) {
        this.certificateMaterialLoader = certificateMaterialLoader;
        this.artifactStoragePort = artifactStoragePort;
    }

    @Override
    public SignedArtifactResult sign(FiscalDocumentEntity document, XmlBuildResult xmlBuildResult, CertificateContext certificateContext) {
        try {
            CertificateMaterial material = certificateMaterialLoader.load(certificateContext);
            Document xmlDoc = parse(xmlBuildResult.xmlContent());

            XMLSignatureFactory sigFactory = XMLSignatureFactory.getInstance("DOM");
            Reference ref = sigFactory.newReference(
                    "",
                    sigFactory.newDigestMethod(DigestMethod.SHA256, null),
                    java.util.List.of(
                            sigFactory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                            sigFactory.newTransform(CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null)
                    ),
                    null,
                    null
            );

            SignedInfo signedInfo = sigFactory.newSignedInfo(
                    sigFactory.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                    sigFactory.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
                    Collections.singletonList(ref)
            );

            X509Certificate certificate = material.certificate();
            KeyInfoFactory keyInfoFactory = sigFactory.getKeyInfoFactory();
            X509Data x509Data = keyInfoFactory.newX509Data(java.util.List.of(certificate));
            KeyInfo keyInfo = keyInfoFactory.newKeyInfo(Collections.singletonList(x509Data));

            XMLSignature signature = sigFactory.newXMLSignature(signedInfo, keyInfo);
            DOMSignContext signContext = new DOMSignContext(material.privateKey(), resolveSignatureParent(xmlDoc));
            signContext.setDefaultNamespacePrefix("ds");
            signature.sign(signContext);

            String signedXml = serialize(xmlDoc);
            StoredArtifactResult stored = artifactStoragePort.storeSignedXml(document, signedXml);

            return new SignedArtifactResult(
                    signedXml,
                    stored.path(),
                    stored.sha256(),
                    "XMLDSIG-" + document.getId(),
                    SignatureMethod.RSA_SHA256
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("Unable to sign fiscal XML artifact");
        }
    }

    private Document parse(String xmlContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));
    }

    private Node resolveSignatureParent(Document doc) {
        Node extensionContent = firstByLocalName(doc.getDocumentElement(), "ExtensionContent");
        if (extensionContent != null) {
            return extensionContent;
        }
        return doc.getDocumentElement();
    }

    private Node firstByLocalName(Element root, String localName) {
        if (localName.equals(root.getLocalName())) {
            return root;
        }
        for (int i = 0; i < root.getChildNodes().getLength(); i++) {
            Node node = root.getChildNodes().item(i);
            if (node instanceof Element child) {
                Node found = firstByLocalName(child, localName);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String serialize(Document document) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }
}
