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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.conf.globalconf.ApprovedCAInfo;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.niis.xroad.securityserver.restapi.util.SpringApplicationContext;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.connector.DefaultConnection;
import org.shredzone.acme4j.connector.HttpConnector;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.toolbox.JSONBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.util.Base64;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;

import static org.niis.xroad.securityserver.restapi.service.AcmeXroadHttpConnector.XROAD_ACME_USER_AGENT;

@Slf4j
public class AcmeProfileIdConnection extends DefaultConnection {

    private KeyUsage keyUsage;

    public AcmeProfileIdConnection(HttpConnector httpConnector) {
        super(httpConnector);
    }

    @Override
    protected int sendSignedRequest(URL url, JSONBuilder claims, Session session,
                                    KeyPair keypair, URL accountLocation, String accept) throws AcmeException {
        if (claims != null && claims.toMap().get("csr") != null) {
            extractKeyUsageFromCsr(claims);
        }
        return super.sendSignedRequest(url, claims, session, keypair, accountLocation, accept);
    }

    private void extractKeyUsageFromCsr(JSONBuilder claims) throws AcmeException {
        String csrBase64Encoded = (String) claims.toMap().get("csr");
        byte[] csrBytes = Base64.getUrlDecoder().decode(csrBase64Encoded);
        try {
            PKCS10CertificationRequest csr = new PKCS10CertificationRequest(csrBytes);
            keyUsage = Optional.ofNullable(csr.getRequestedExtensions())
                    .map(KeyUsage::fromExtensions)
                    .orElse(null);
        } catch (IOException e) {
            throw new AcmeException("Error reading csr for key usage information", e);
        }
    }

    @Override
    protected void sendRequest(Session session, URL url, Consumer<HttpRequest.Builder> body) throws IOException {
        var builder = httpConnector.createRequestBuilder(url)
                .header("Accept-Charset", "utf-8")
                .header("Accept-Language", session.getLanguageHeader());

        if (session.networkSettings().isCompressionEnabled()) {
            builder.header("Accept-Encoding", "gzip");
        }

        if (keyUsage != null) {
            String profileId = buildProfileIdHeader(session);
            builder.setHeader("User-Agent", profileId + XROAD_ACME_USER_AGENT);
        }

        body.accept(builder);
        try {
            lastResponse = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException ex) {
            throw new IOException("Request was interrupted", ex);
        }
    }

    private String buildProfileIdHeader(Session session) {
        GlobalConfService globalConfService = SpringApplicationContext.getBean(GlobalConfService.class);
        Collection<ApprovedCAInfo> approvedCAsForThisInstance = globalConfService.getApprovedCAsForThisInstance();
        ApprovedCAInfo approvedCA = approvedCAsForThisInstance.stream()
                .filter(ca -> isCABeingConnectedTo(session, ca))
                .findFirst().orElseThrow();
        String profileId = "profileID=";
        if (keyUsage.hasUsages(KeyUsage.nonRepudiation)) {
            profileId += approvedCA.getSigningCertificateProfileId();
        } else {
            profileId += approvedCA.getAuthenticationCertificateProfileId();
        }
        profileId += " ";
        return profileId;
    }

    private static boolean isCABeingConnectedTo(Session session, ApprovedCAInfo ca) {
        if (ca.getAcmeServerDirectoryUrl() == null) {
            return false;
        }
        String dirUrlHost = URI.create(ca.getAcmeServerDirectoryUrl()).getHost();
        return session.getServerUri().getHost().equals(dirUrlHost);
    }
}
