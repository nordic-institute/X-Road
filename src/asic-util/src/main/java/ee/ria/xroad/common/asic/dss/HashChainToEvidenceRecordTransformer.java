package ee.ria.xroad.common.asic.dss;

import ee.ria.xroad.common.hashchain.AbstractValueType;
import ee.ria.xroad.common.hashchain.DataRefType;
import ee.ria.xroad.common.hashchain.HashChainType;
import ee.ria.xroad.common.hashchain.HashChainVerifier;
import ee.ria.xroad.common.hashchain.HashStepType;
import ee.ria.xroad.common.hashchain.HashValueType;
import ee.ria.xroad.common.util.CryptoUtils;

import com.google.common.collect.Lists;
import eu.europa.esig.asic.manifest.ASiCManifestUtils;
import eu.europa.esig.asic.manifest.jaxb.ASiCManifestType;
import eu.europa.esig.dss.asic.common.ASiCUtils;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.xml.common.SchemaFactoryBuilder;
import eu.europa.esig.dss.xml.common.XmlDefinerUtils;
import eu.europa.esig.dss.xml.utils.XMLCanonicalizer;
import eu.europa.esig.xmlers.XMLEvidenceRecordFacade;
import eu.europa.esig.xmlers.jaxb.EvidenceRecordType;
import eu.europa.esig.xmlers.jaxb.HashTreeType;
import eu.europa.esig.xmlers.jaxb.ObjectFactory;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static ee.ria.xroad.common.hashchain.HashChainConstants.CANONICALIZATION_METHOD;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class HashChainToEvidenceRecordTransformer {
    private static final ObjectFactory OBJECT_FACTORY = new ObjectFactory();

    private static final String SIGN_REF_URI = "META-INF/evidencerecord.xml";
    private static final String SIGNATURE_URI = "META-INF/signatures.xml"; //TODO dss usually uses signatures001, consider changing

    private final XMLEvidenceRecordFacade evidenceRecordFacade = XMLEvidenceRecordFacade.newFacade();
    private final EvidenceRecordFactory evidenceRecordFactory = new EvidenceRecordFactory();
    private final EvidenceRecordManifestFactory manifestFactory = new EvidenceRecordManifestFactory();

    static {
        // force usage of internal xerces implementation in DSS. Otherwise, not compatible Apache Xerces will be used in proxy
        // can be removed once Apache Xerces is removed from classpath
        XmlDefinerUtils.getInstance().setSchemaFactoryBuilder(new JaxpSchemaFactoryBuilder());
    }

    @SneakyThrows //TODO
    public List<DSSDocument> createEvidenceRecord(String timestampToken, String hashChainXml, String hashChainResulXml, String inputSignature) {

        //string to inputStream

        var hashChainType = HashChainVerifier.parseHashChain(new ByteArrayInputStream(hashChainXml.getBytes(StandardCharsets.UTF_8)));

        //not required. just for reference for POC
        //contains root hash and data ref to that hash node.
        var hashChainResult = HashChainVerifier
                .parseHashChainResult(new ByteArrayInputStream(hashChainResulXml.getBytes(StandardCharsets.UTF_8)));


        var digestAlgorithm = hashChainType.getDefaultDigestMethod().getAlgorithm();

        var canonizedSigDigest = signatureHash(inputSignature);
        var hashTreeResult = createHashTree(hashChainType, canonizedSigDigest);


        var evidenceRecordType = evidenceRecordFactory.createEvidenceRecord(hashTreeResult.hashTree(), digestAlgorithm, timestampToken);
        var signatureDigest = calculateDigest(CryptoUtils.SHA512_ID, inputSignature.getBytes(UTF_8));
        var aSiCManifestType = manifestFactory.createAsicManifest(SIGN_REF_URI,
                SIGNATURE_URI, signatureDigest, digestAlgorithm);

        return List.of(marshall(evidenceRecordType), marshall(aSiCManifestType));
    }


    private DSSDocument marshall(EvidenceRecordType evidenceRecordType) throws JAXBException, IOException, SAXException {
        var evidenceRecordXml = evidenceRecordFacade.marshall(evidenceRecordType);

        return new InMemoryDocument(evidenceRecordXml.getBytes(StandardCharsets.UTF_8), ASiCUtils.EVIDENCE_RECORD_XML);
    }


    private DSSDocument marshall(JAXBElement<ASiCManifestType> aSiCManifestType) throws IOException, JAXBException {
        var marshaller = ASiCManifestUtils.getInstance().getJAXBContext().createMarshaller(); //TODO const?

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            marshaller.marshal(aSiCManifestType, outputStream);
            return new InMemoryDocument(outputStream.toByteArray(), "META-INF/" + ASiCUtils.ASIC_EVIDENCE_RECORD_MANIFEST_FILENAME + "001" + ".xml");
        }
    }

    private HashChainResult createHashTree(HashChainType hashChainType, byte[] inputSignatureDigest) {
        var hashTree = OBJECT_FACTORY.createHashTreeType();

        //TODO xroad8 HashChainVerifier has better approach of working with nodes. This is just a quick example assuming
        // that order is always correct.

        hashTree.getSequence().add(createSequence(1, inputSignatureDigest));
        byte[] dataRefDigestValue = null;
        //reverse order
        var reverse = Lists.reverse(hashChainType.getHashStep());
        int index = 1;
        for (HashStepType hashStep : reverse) {
            var hashStepData = resolveHashStepData(hashStep);

            if (hashStepData.dataRefDigestValue != null) {
                log.debug("Data ref detected in step {}", hashStep.getId());
                dataRefDigestValue = hashStepData.dataRefDigestValue;
            }

            hashTree.getSequence().add(createSequence(++index /*+ 1*/, hashStepData.hashDigestValue));
        }


        return new HashChainResult(hashTree, dataRefDigestValue);
    }

    private HashStepData resolveHashStepData(HashStepType hashStep) {
        byte[] digestValue = null; //TODO handle missing digest value
        byte[] dataRefDigestValue = null;
        for (AbstractValueType abstractValueType : hashStep.getHashValueOrStepRefOrDataRef()) {

            if (abstractValueType instanceof HashValueType hashValueType) {
                digestValue = hashValueType.getDigestValue();
            } else if (abstractValueType instanceof DataRefType dataRefType) {
                dataRefDigestValue = dataRefType.getDigestValue();
            }
        }

        int order = Integer.parseInt(hashStep.getId().split("STEP")[1]) + 1; // evidence records use 1 based index
        return new HashStepData(order, digestValue, dataRefDigestValue);
    }

    private record HashStepData(int order, byte[] hashDigestValue, byte[] dataRefDigestValue) {
    }

    private record HashChainResult(HashTreeType hashTree, byte[] dataRefDigestValue) {
    }

    private HashTreeType.Sequence createSequence(int order, byte[] digestValue) {
        var sequence = OBJECT_FACTORY.createHashTreeTypeSequence();
        sequence.setOrder(order);
        sequence.getDigestValue().add(digestValue);
        return sequence;
    }

    //TODO xroad8 - duplicated, consolidate.
    public static class JaxpSchemaFactoryBuilder extends SchemaFactoryBuilder {
        @Override
        protected SchemaFactory instantiateFactory() {
            return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI,
                    "com.sun.org.apache.xerces.internal.jaxp.validation.XMLSchemaFactory", null);
        }
    }

    static byte[] signatureHash(String signatureXml) throws Exception {
        byte[] canonicalizedDocument = XMLCanonicalizer.createInstance(CANONICALIZATION_METHOD).canonicalize(signatureXml.getBytes(UTF_8));
        return calculateDigest(CryptoUtils.SHA512_ID, canonicalizedDocument);
    }
}
