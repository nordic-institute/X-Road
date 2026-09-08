/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.confproxy.core.api.resource;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jboss.resteasy.reactive.RestResponse;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.confproxy.common.domain.ConfProxyInstance;
import org.niis.xroad.confproxy.common.service.AnchorGenerator;
import org.niis.xroad.confproxy.common.service.ConfProxyInstanceService;
import org.niis.xroad.confproxy.core.api.dto.AddSigningKeyRequest;
import org.niis.xroad.confproxy.core.api.dto.InstanceResponse;
import org.niis.xroad.confproxy.core.api.dto.InstancesResponse;
import org.niis.xroad.confproxy.core.service.SignerService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import static org.niis.xroad.confproxy.common.service.ConfProxyInstanceService.CertInfo.State.OK;

@Slf4j
@Path("/v1/instances")
@RolesAllowed("XROAD_SYSTEM_ADMINISTRATOR")
@ApplicationScoped
@RequiredArgsConstructor
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InstanceResource {

    private static final String ANCHOR_NAME_TPL = "proxy_anchor_%s_%s.xml";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ConfProxyInstanceService confProxyInstanceService;
    private final SignerService signerService;
    private final AnchorGenerator anchorGenerator;

    @GET
    public RestResponse<InstancesResponse> getInstances() {
        var responses = new InstancesResponse(Set.copyOf(confProxyInstanceService.availableInstancesNames()));
        return RestResponse.ok(responses);
    }

    @GET
    @Path("/{name}")
    public RestResponse<InstanceResponse> getInstance(String name) {
        var instance = confProxyInstanceService.loadInstance(name);
        return RestResponse.ok(map(instance));
    }

    @POST
    @Path("/{name}/signing-key")
    public RestResponse<InstanceResponse> addSigningKey(String name, AddSigningKeyRequest request) {
        var instance = confProxyInstanceService.loadInstance(name);

        if (StringUtils.isBlank(request.keyId()) && StringUtils.isBlank(request.tokenId())) {
            throw XrdRuntimeException.systemException(ErrorCode.BAD_REQUEST, "Missing key or token ID");
        }

        if (StringUtils.isNotBlank(request.keyId()) && StringUtils.isNotBlank(request.tokenId())) {
            throw XrdRuntimeException.systemException(ErrorCode.BAD_REQUEST, "Either key id or token id is required not both");
        }

        SignerService.KeyCert newKey;
        if (StringUtils.isNotBlank(request.tokenId())) {
            newKey = signerService.createCert(request.tokenId(), getKeyAlgorithm(request));
        } else {
            newKey = signerService.createCert(request.keyId());
        }

        confProxyInstanceService.addSigningKey(instance, newKey.keyId(), newKey.cert());

        if (!newKey.keyId().equals(instance.getActiveSigningKey()) && request.asActive()) {
            confProxyInstanceService.setActiveSigningKey(instance, newKey.keyId());
        }

        return RestResponse.ok(map(instance));
    }

    @PATCH
    @Path("/{name}/signing-key/{keyId}/set-active")
    public RestResponse<InstanceResponse> setActiveSigningKey(String name, String keyId) {
        var instance = confProxyInstanceService.loadInstance(name);

        var info = confProxyInstanceService.certInfo(instance, keyId);
        if (info.isEmpty()) {
            throw XrdRuntimeException.systemException(ErrorCode.NOT_FOUND, "Key with ID: " + keyId + " not found");
        }

        if (OK != info.get().state()) {
            throw XrdRuntimeException.systemInternalError("Selected key ID is associated with invalid certificate: " + info.get());
        }

        confProxyInstanceService.setActiveSigningKey(instance, keyId);

        return RestResponse.ok(map(instance));
    }

    @DELETE
    @Path("/{name}/signing-key/{keyId}")
    public RestResponse<InstanceResponse> removeSigningKey(String name, String keyId) {
        var instance = confProxyInstanceService.loadInstance(name);

        if (keyId.equals(instance.getActiveSigningKey())) {
            throw XrdRuntimeException.systemInternalError("Not allowed to delete an active signing key!");
        }

        if (!confProxyInstanceService.removeKeyId(instance, keyId)) {
            throw XrdRuntimeException.systemException(ErrorCode.NOT_FOUND, "Key with ID: " + keyId + " not found");
        }

        signerService.deleteKey(keyId);

        return RestResponse.ok(map(instance));
    }

    @GET
    @Path("/{name}/anchor")
    @Produces(MediaType.APPLICATION_XML)
    public RestResponse<byte[]> generateAnchor(String name) {
        var instance = confProxyInstanceService.loadInstance(name);

        var bytes = anchorGenerator.generateAnchor(instance);
        var filename = ANCHOR_NAME_TPL.formatted(name, FORMATTER.format(LocalDateTime.now()));

        return RestResponse.ResponseBuilder.ok(bytes)
                .header(HttpHeaders.CONTENT_DISPOSITION, filename)
                .build();

    }

    private KeyAlgorithm getKeyAlgorithm(AddSigningKeyRequest request) {
        return request.keyAlgorithm() == null ? KeyAlgorithm.RSA : request.keyAlgorithm();
    }

    private InstanceResponse map(ConfProxyInstance instance) {
        InstanceResponse.Anchor anchor = null;
        String anchorError = null;
        try {
            anchor = mapAnchor(instance);
        } catch (Exception e) {
            anchorError = e.getMessage();
        }

        return new InstanceResponse(
                instance.getInstance(),
                instance.getInstanceConfigurationPath(),
                anchor,
                anchorError,
                instance.getValidityIntervalSeconds(),
                mapKeyCerts(instance),
                instance.isReady());
    }

    private List<InstanceResponse.KeyCert> mapKeyCerts(ConfProxyInstance instance) {
        return instance.getKeyList().stream()
                .map(key -> new InstanceResponse.KeyCert(
                        key.equals(instance.getActiveSigningKey()),
                        key,
                        confProxyInstanceService.certInfo(instance, key)
                                .map(Object::toString).
                                orElse("")))
                .toList();
    }

    private InstanceResponse.Anchor mapAnchor(ConfProxyInstance instance) {
        var source = confProxyInstanceService.anchorHash(instance);
        return new InstanceResponse.Anchor(
                source.anchor().getInstanceIdentifier(),
                source.anchor().getGeneratedAt().toInstant(),
                source.hash());
    }
}
