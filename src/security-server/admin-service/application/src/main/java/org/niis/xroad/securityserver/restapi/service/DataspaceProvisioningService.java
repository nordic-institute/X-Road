/*
 * The MIT License
 *
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

import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties.Dataspace;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;

/**
 * Provisions this security server's data space participant contexts (IdentityHub + Control Plane)
 * and issues their X-Road membership credentials.
 *
 * <p>For each participant context (the host context, plus the MANAGEMENT context when enabled) the service
 * creates the IdentityHub participant context, the Control Plane participant context and its STS-bound config,
 * then requests the MembershipCredential. The creates are idempotent (re-runs tolerate conflicts) and the
 * credential is the success gate.</p>
 *
 * <p>The IdentityHub credential request reaches a terminal {@code ERROR} state when its prerequisites
 * (the member SIGN certificate plus a fresh OCSP response, propagated global conf) are not yet ready, and
 * re-submitting the same {@code holderPid} is a no-op. To recover, this service probes a sequence of
 * {@code holderPid} slots and submits a fresh request once the previous one has errored, so that repeated
 * invocations converge to {@code ISSUED} once the prerequisites become observable.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataspaceProvisioningService {

    private static final String IDENTITY_API = "/api/identity/v1alpha/participants";
    private static final String MANAGEMENT_API = "/api/management/v5beta/participants";
    private static final String HOLDER_PID_BASE = "xroad-membership-credential-request";
    private static final String BEARER = "Bearer ";
    private static final String MANAGEMENT_CONTEXT_SUFFIX = "-mgmt";
    private static final String CREDENTIAL_FORMAT = "VC1_0_JWT";
    private static final String CREDENTIAL_TYPE = "MembershipCredential";
    private static final int DID_PORT = 7183;
    private static final int STS_PORT = 7184;
    private static final int CREDENTIAL_PORT = 7185;
    private static final String STATE_ISSUED = "ISSUED";
    private static final String STATE_ERROR = "ERROR";
    private static final String STATUS_ISSUED = "ISSUED";
    private static final String STATUS_PENDING = "PENDING";

    private final AdminServiceProperties adminServiceProperties;
    private final ServerConfService serverConfService;

    /**
     * Ensures every configured participant context is provisioned and holds a membership credential.
     *
     * @return {@code ISSUED} when all contexts are issued, {@code PENDING} while issuance is still in progress
     */
    public String provision() {
        Dataspace ds = adminServiceProperties.getDataspace();
        if (!ds.isEnabled()) {
            throw XrdRuntimeException.systemException(INTERNAL_ERROR)
                    .details("Data space provisioning is not enabled")
                    .build();
        }

        String memberId = memberIdSlashForm();
        String identityHubHost = hostOf(ds.getIdentityHubUrl());

        boolean allIssued = true;
        try (CloseableHttpClient client = createHttpClient(ds)) {
            for (String participantId : participantContexts(ds)) {
                ensureParticipantContext(client, ds, participantId, identityHubHost, memberId);
                String status = ensureCredential(client, ds, participantId);
                log.info("Data space credential status for participant {}: {}", participantId, status);
                if (!STATUS_ISSUED.equals(status)) {
                    allIssued = false;
                }
            }
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(INTERNAL_ERROR)
                    .cause(e)
                    .details("Error during data space provisioning")
                    .build();
        }
        return allIssued ? STATUS_ISSUED : STATUS_PENDING;
    }

    private List<String> participantContexts(Dataspace ds) {
        List<String> participantIds = new ArrayList<>();
        participantIds.add(ds.getParticipantId());
        if (ds.isManagementContextEnabled()) {
            participantIds.add(ds.getParticipantId() + MANAGEMENT_CONTEXT_SUFFIX);
        }
        return participantIds;
    }

    private void ensureParticipantContext(CloseableHttpClient client, Dataspace ds, String participantId,
                                          String identityHubHost, String memberId) {
        String did = didFor(identityHubHost, participantId);
        createIdentityHubContext(client, ds, participantId, did, identityHubHost, memberId);
        createControlPlaneContext(client, ds, participantId, did);
        createControlPlaneContextConfig(client, ds, participantId, did, identityHubHost);
    }

    private String didFor(String identityHubHost, String participantId) {
        String did = "did:web:" + identityHubHost + "%3A" + DID_PORT;
        return participantId.endsWith(MANAGEMENT_CONTEXT_SUFFIX) ? did + ":mgmt" : did;
    }

    private void createIdentityHubContext(CloseableHttpClient client, Dataspace ds, String participantId, String did,
                                          String identityHubHost, String memberId) {
        String credentialService = "https://%s:%d/api/credentials/v1/participants/%s"
                .formatted(identityHubHost, CREDENTIAL_PORT, participantId);
        String body = """
                {
                    "roles": [],
                    "serviceEndpoints": [{
                        "type": "CredentialService",
                        "serviceEndpoint": "%s",
                        "id": "%s-credential-service"
                    }],
                    "active": true,
                    "additionalProperties": { "xroadMemberId": "%s" },
                    "participantContextId": "%s",
                    "did": "%s",
                    "key": {
                        "keyId": "%s#key-1",
                        "privateKeyAlias": "%s-key",
                        "keyGeneratorParams": { "algorithm": "EdDSA" }
                    }
                }""".formatted(credentialService, did, memberId, participantId, did, did, did);
        HttpPost post = new HttpPost(ds.getIdentityHubUrl() + IDENTITY_API);
        post.addHeader(HttpHeaders.AUTHORIZATION, BEARER + ds.getIdentityToken());
        post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        executeWrite(client, post, "IH participant context " + participantId);
    }

    private void createControlPlaneContext(CloseableHttpClient client, Dataspace ds, String participantId, String did) {
        String body = """
                {
                    "@context": ["https://w3id.org/edc/connector/management/v2"],
                    "@type": "ParticipantContext",
                    "identity": "%s",
                    "@id": "%s"
                }""".formatted(did, participantId);
        HttpPost post = new HttpPost(ds.getControlPlaneUrl() + MANAGEMENT_API);
        post.addHeader(HttpHeaders.AUTHORIZATION, BEARER + ds.getControlPlaneToken());
        post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        executeWrite(client, post, "CP participant context " + participantId);
    }

    private void createControlPlaneContextConfig(CloseableHttpClient client, Dataspace ds, String participantId,
                                                 String did, String identityHubHost) {
        String stsTokenUrl = "https://%s:%d/api/sts/token".formatted(identityHubHost, STS_PORT);
        String body = """
                {
                    "@context": ["https://w3id.org/edc/connector/management/v2"],
                    "@type": "ParticipantContextConfig",
                    "entries": {
                        "edc.participant.id": "%s",
                        "edc.participant.did": "%s",
                        "edc.iam.sts.oauth.token.url": "%s",
                        "edc.iam.sts.oauth.client.id": "%s",
                        "edc.iam.sts.oauth.client.secret.alias": "%s-sts-client-secret"
                    },
                    "privateEntries": {}
                }""".formatted(did, did, stsTokenUrl, did, participantId);
        HttpPut put = new HttpPut(ds.getControlPlaneUrl() + MANAGEMENT_API + "/" + participantId + "/config");
        put.addHeader(HttpHeaders.AUTHORIZATION, BEARER + ds.getControlPlaneToken());
        put.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        executeWrite(client, put, "CP participant context config " + participantId);
    }

    private void executeWrite(CloseableHttpClient client, HttpUriRequest request, String label) {
        try (CloseableHttpResponse response = client.execute(request)) {
            int code = response.getStatusLine().getStatusCode();
            String body = readBody(response);
            if (code >= HttpStatus.SC_BAD_REQUEST) {
                log.warn("DSP provisioning '{}' -> HTTP {} (tolerated). Body: {}", label, code, body);
            } else {
                log.info("DSP provisioning '{}' -> HTTP {}", label, code);
            }
        } catch (IOException e) {
            log.warn("DSP provisioning '{}' failed: {}", label, e.getMessage());
        }
    }

    private static String readBody(CloseableHttpResponse response) throws IOException {
        var entity = response.getEntity();
        return entity == null ? "" : EntityUtils.toString(entity);
    }

    private String memberIdSlashForm() {
        ClientId owner = serverConfService.getSecurityServerOwnerId();
        return "%s/%s/%s".formatted(owner.getXRoadInstance(), owner.getMemberClass(), owner.getMemberCode());
    }

    private String hostOf(String url) {
        return URI.create(url).getHost();
    }

    private String ensureCredential(CloseableHttpClient client, Dataspace ds, String participantId) {
        for (int slot = 0; slot < ds.getMaxHolderPidSlots(); slot++) {
            String holderPid = holderPid(participantId, slot);
            String state = fetchRequestState(client, ds, participantId, holderPid);
            if (state == null) {
                submitRequest(client, ds, participantId, holderPid);
                state = pollUntilSettled(client, ds, participantId, holderPid);
            }
            if (STATE_ISSUED.equals(state)) {
                return STATUS_ISSUED;
            }
            if (STATE_ERROR.equals(state)) {
                continue;
            }
            return STATUS_PENDING;
        }
        log.warn("Data space credential for participant {} exhausted {} holder request slots, all in ERROR",
                participantId, ds.getMaxHolderPidSlots());
        return STATUS_PENDING;
    }

    private String holderPid(String participantId, int slot) {
        String base = participantId + "-" + HOLDER_PID_BASE;
        return slot == 0 ? base : base + "-" + slot;
    }

    private String pollUntilSettled(CloseableHttpClient client, Dataspace ds, String participantId, String holderPid) {
        long deadline = System.currentTimeMillis() + ds.getPollTimeoutMillis();
        String state = fetchRequestState(client, ds, participantId, holderPid);
        while (!isTerminal(state) && System.currentTimeMillis() < deadline) {
            sleep(ds.getPollIntervalMillis());
            state = fetchRequestState(client, ds, participantId, holderPid);
        }
        return state;
    }

    private boolean isTerminal(String state) {
        return STATE_ISSUED.equals(state) || STATE_ERROR.equals(state);
    }

    private String fetchRequestState(CloseableHttpClient client, Dataspace ds, String participantId, String holderPid) {
        String url = ds.getIdentityHubUrl() + IDENTITY_API + "/" + participantId + "/credentials/request/" + holderPid;
        HttpGet get = new HttpGet(url);
        get.addHeader(HttpHeaders.AUTHORIZATION, BEARER + ds.getIdentityToken());
        try (CloseableHttpResponse response = client.execute(get)) {
            int code = response.getStatusLine().getStatusCode();
            if (code == HttpStatus.SC_NOT_FOUND) {
                return null;
            }
            String body = readBody(response);
            if (code >= HttpStatus.SC_INTERNAL_SERVER_ERROR) {
                log.warn("IdentityHub returned {} for credential request {}/{}: {}", code, participantId, holderPid, body);
                return null;
            }
            JsonNode json = JsonMapper.builder().build().readTree(body);
            return json.has("status") ? json.get("status").asString() : null;
        } catch (IOException e) {
            log.warn("Error fetching credential request {}/{}: {}", participantId, holderPid, e.getMessage());
            return null;
        }
    }

    private void submitRequest(CloseableHttpClient client, Dataspace ds, String participantId, String holderPid) {
        String url = ds.getIdentityHubUrl() + IDENTITY_API + "/" + participantId + "/credentials/request";
        String body = """
                {"issuerDid":"%s","holderPid":"%s","credentials":[{"format":"%s","type":"%s","id":"%s"}]}"""
                .formatted(ds.getIssuerDid(), holderPid, CREDENTIAL_FORMAT, CREDENTIAL_TYPE, ds.getCredentialDefinitionId());
        HttpPost post = new HttpPost(url);
        post.addHeader(HttpHeaders.AUTHORIZATION, BEARER + ds.getIdentityToken());
        post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        executeWrite(client, post, "credential request " + holderPid + " for " + participantId);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw XrdRuntimeException.systemException(INTERNAL_ERROR)
                    .cause(e)
                    .details("Interrupted while polling data space credential request")
                    .build();
        }
    }

    @SuppressWarnings("java:S4830") // co-located internal traffic to the data space components, addressed by service name
    private CloseableHttpClient createHttpClient(Dataspace ds) {
        try {
            TrustManager trustAll = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    // not used by a client
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    // co-located data space components are trusted
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[]{};
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustAll}, new SecureRandom());

            int timeout = ds.getRequestTimeoutMillis();
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(timeout)
                    .setConnectionRequestTimeout(timeout)
                    .setSocketTimeout(timeout)
                    .build();

            return HttpClients.custom()
                    .setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE))
                    .setDefaultRequestConfig(requestConfig)
                    .disableAutomaticRetries()
                    .build();
        } catch (GeneralSecurityException e) {
            throw XrdRuntimeException.systemException(INTERNAL_ERROR)
                    .cause(e)
                    .details("Unable to initialize data space provisioning client")
                    .build();
        }
    }
}
