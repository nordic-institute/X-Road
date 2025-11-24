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
package org.niis.xroad.proxy.core.signature;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.crypto.Signatures;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.signature.SignatureResourceResolver;
import ee.ria.xroad.common.signature.SigningRequest;

import jakarta.xml.bind.JAXBException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.xml.security.exceptions.XMLSecurityException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

/**
 * This class handles the (batch) signature creation. After requests
 * have been added to the context, the signature is created.
 * <p>
 * Depending on the amount of input hashes (one hash for a single message,
 * multiple hashes for a single message with attachments etc.) the result
 * is a XML signature with one referenced message or a referenced hash chain
 * result with corresponding hash chains.
 */
@Slf4j
public class SignatureCtx {

    protected final List<SigningRequest> requests = new ArrayList<>();

    @Getter
    protected final SignAlgorithm signatureAlgorithmId;

    protected SignatureXmlBuilder builder;

    public SignatureCtx(SignAlgorithm signatureAlgorithmId) {
        this.signatureAlgorithmId = signatureAlgorithmId;
    }

    /**
     * Adds a new signing request to this context.
     */
    public synchronized void add(SigningRequest request) {
        requests.add(request);
    }

    /**
     * Produces the XML signature from the given signed data.
     */
    public synchronized String createSignatureXml(byte[] signatureValue) throws IOException, TransformerException {
        return builder.createSignatureXml(Signatures.useRawFormat(signatureAlgorithmId, signatureValue));
    }

    /**
     * Returns the data to be signed -- if there is only one signing request
     * and the request is simple message (no attachments), then no hash chain is used.
     */
    public synchronized byte[] getDataToBeSigned()
            throws CertificateEncodingException, ParserConfigurationException, IOException, XMLSecurityException, JAXBException {
        log.trace("getDataToBeSigned(requests = {})", requests.size());

        if (requests.isEmpty()) {
            throw new CodedException(X_INTERNAL_ERROR, "No requests in signing context");
        }

        SigningRequest firstRequest = requests.getFirst();

        builder = new SignatureXmlBuilder(firstRequest, signatureAlgorithmId);

        return builder.addAndCalculateDataToBeSigned(new SignatureResourceResolver(firstRequest.getParts(), null));
    }

}
