/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.asic.dss;

import ee.ria.xroad.common.asic.TimestampData;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.MessageFileNames;

import eu.europa.esig.dss.FileNameBuilder;
import eu.europa.esig.dss.asic.common.ASiCContent;
import eu.europa.esig.dss.asic.common.ASiCUtils;
import eu.europa.esig.dss.asic.common.ZipUtils;
import eu.europa.esig.dss.asic.xades.signature.ASiCWithXAdESASiCContentBuilder;
import eu.europa.esig.dss.asic.xades.signature.ASiCWithXAdESFilenameFactory;
import eu.europa.esig.dss.asic.xades.signature.DefaultASiCWithXAdESFilenameFactory;
import eu.europa.esig.dss.asic.xades.signature.asice.ASiCEWithXAdESManifestBuilder;
import eu.europa.esig.dss.enumerations.ASiCContainerType;
import eu.europa.esig.dss.enumerations.MimeType;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.signature.SigningOperation;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static ee.ria.xroad.common.asic.AsicContainerEntries.ENTRY_MIMETYPE;
import static ee.ria.xroad.common.asic.AsicContainerEntries.MIMETYPE;


@Slf4j
public class DSSASiCBuilder {
    private final ASiCWithXAdESFilenameFactory asicFilenameFactory = new DefaultASiCWithXAdESFilenameFactory();

    public static DSSASiCBuilder newBuilder() {
        return new DSSASiCBuilder();
    }

    public DSSDocument createContainer(byte[] plainTextMessage, InputStream plainAttachment,
                                       SignatureData signatureData, TimestampData timestamp, long creationTime) {
        List<DSSDocument> dssDocs = new ArrayList<>();
        dssDocs.add(new InMemoryDocument(MIMETYPE.getBytes(StandardCharsets.UTF_8), ENTRY_MIMETYPE));
        dssDocs.add(new InMemoryDocument(plainTextMessage, MessageFileNames.MESSAGE));
        if (plainAttachment != null) {
            dssDocs.add(new InMemoryDocument(plainAttachment, MessageFileNames.attachment(1)));
        }
        var signature = new InMemoryDocument(signatureData.getSignatureXml().getBytes(), "signatures.xml");
        var creationDate = new Date(creationTime);

        List<DSSDocument> evidenceRecords = new ArrayList<>();
        if (timestamp != null) {
            log.trace("Timestamp found in container. Adding evidence record..");

            var evidenceRecordDocs = new HashChainToEvidenceRecordTransformer()
                    .createEvidenceRecord(
                            timestamp.getTimestampBase64(),
                            timestamp.getHashChain(),
                            timestamp.getHashChainResult(),
                            signatureData.getSignatureXml());
            evidenceRecords.addAll(evidenceRecordDocs);
        }

        return createContainer(dssDocs, evidenceRecords, signature, creationDate);
    }

    private DSSDocument createContainer(List<DSSDocument> containerContent,
                                        List<DSSDocument> evidenceRecords, DSSDocument signature, Date creationTime) {
        Objects.requireNonNull(containerContent, "toSignDocument cannot be null!");

        ASiCContent asicContent = new ASiCWithXAdESASiCContentBuilder()
                .build(containerContent, ASiCContainerType.ASiC_E);

        //TODO xroad uses asic-s specific name..
        //signature.setName(asicFilenameFactory.getSignatureFilename(asicContent));
        signature.setName(ASiCUtils.SIGNATURES_XML);
        ASiCUtils.addOrReplaceDocument(asicContent.getSignatureDocuments(), signature);

        DSSDocument asicManifest = createASiCManifest(asicContent);
        asicContent.getManifestDocuments().add(asicManifest);
        asicContent.getEvidenceRecordDocuments().addAll(evidenceRecords);
        final DSSDocument asicSignature = buildASiCContainer(asicContent, creationTime);
        asicSignature.setName(getFinalDocumentName(asicSignature, SigningOperation.SIGN, SignatureLevel.XAdES_BASELINE_B, null, asicSignature.getMimeType()));

        return asicSignature;
    }

    protected DSSDocument buildASiCContainer(ASiCContent asicContent, Date creationTime) {
        DSSDocument zipArchive = ZipUtils.getInstance().createZipArchive(asicContent, creationTime);
        zipArchive.setMimeType(ASiCUtils.getMimeType(asicContent.getMimeTypeDocument()));
        return zipArchive;
    }

    protected String getFinalDocumentName(DSSDocument originalFile, SigningOperation operation, SignatureLevel level,
                                          SignaturePackaging packaging, MimeType containerMimeType) {
        return new FileNameBuilder().setOriginalFilename(originalFile.getName()).setSigningOperation(operation)
                .setSignatureLevel(level).setSignaturePackaging(packaging).setMimeType(containerMimeType).build();
    }

    private DSSDocument createASiCManifest(ASiCContent asicContent) {
        return new ASiCEWithXAdESManifestBuilder().setDocuments(asicContent.getSignedDocuments())
                .setManifestFilename(asicFilenameFactory.getManifestFilename(asicContent)).build();
    }

}
