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
package org.niis.xroad.ds.identityhub.did;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.identityhub.spi.did.DidWebParser;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.ds.identity.DspConventions;
import org.niis.xroad.ds.identity.MemberParticipant;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.eclipse.edc.identityhub.spi.did.DidConstants.DID_WEB_DID_DOCUMENT;
import static org.eclipse.edc.identityhub.spi.webcontext.IdentityHubApiContext.CREDENTIALS;
import static org.eclipse.edc.identityhub.spi.webcontext.IdentityHubApiContext.IH_DID;

/**
 * Serves a synthesised, keyless DID document for a member that is a registered client of this
 * security server but whose participant context has not been provisioned yet, so that the member
 * is resolvable from the moment it is registered (XRDADR-43).
 *
 * <p>Runs behind EDC's {@code DidWebController}, which answers an unknown {@code did.json} path
 * with an empty body. Only that case is examined: the requested path is decoded through the
 * participant identifier scheme, the DID's authority must be this server's GlobalConf-registered
 * address at the identity hub's DID port, and the member, or one of its subsystems, must be a
 * client of this server in GlobalConf. The document carries the same service entries a
 * provisioned member's document does and no verification keys, and is served with
 * {@code Cache-Control: no-store}. Anything else, including any failure while looking the member
 * up, leaves the controller's response untouched.
 */
@RequiredArgsConstructor
class RegisteredMemberDidDocumentFilter implements ContainerResponseFilter {

    private static final String NO_STORE = "no-store";

    private final GlobalConfProvider globalConfProvider;
    private final PortMappingRegistry portMappingRegistry;
    private final Monitor monitor;
    private final DidWebParser didWebParser = new DidWebParser();

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        if (!isEmptyDidDocumentResponse(requestContext, responseContext)) {
            return;
        }
        var requestUrl = requestContext.getUriInfo().getAbsolutePath();
        try {
            synthesise(didWebParser.parse(requestUrl, StandardCharsets.UTF_8)).ifPresent(document -> {
                responseContext.setStatus(Response.Status.OK.getStatusCode());
                responseContext.setEntity(document, null, MediaType.APPLICATION_JSON_TYPE);
                responseContext.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL, NO_STORE);
            });
        } catch (RuntimeException e) {
            monitor.warning("Could not determine whether '%s' names a registered member of this server; "
                    + "leaving the DID unresolved".formatted(requestUrl), e);
        }
    }

    private static boolean isEmptyDidDocumentResponse(ContainerRequestContext requestContext,
                                                      ContainerResponseContext responseContext) {
        return responseContext.getStatus() == Response.Status.NO_CONTENT.getStatusCode()
                && !responseContext.hasEntity()
                && requestContext.getUriInfo().getPath().endsWith(DID_WEB_DID_DOCUMENT);
    }

    /**
     * The keyless document for {@code did}, or empty when the DID does not name a member this
     * server hosts.
     */
    Optional<DidDocument> synthesise(String did) {
        MemberParticipant member = decodeMember(did);
        if (member == null) {
            return Optional.empty();
        }
        var hostingServer = hostingServer(member);
        if (hostingServer.isEmpty() || !isRegisteredClient(member.member(), hostingServer.get().id())) {
            return Optional.empty();
        }
        var credentialsPort = port(CREDENTIALS);
        if (credentialsPort.isEmpty()) {
            return Optional.empty();
        }
        var ctxId = ParticipantIdentifierScheme.memberCtxId(member.member());
        var credentialService = new Service(DspConventions.credentialServiceId(ctxId), DspConventions.CREDENTIAL_SERVICE_TYPE,
                DspConventions.credentialServiceUrl(hostingServer.get().address(), credentialsPort.getAsInt(), ctxId));
        return Optional.of(DidDocument.Builder.newInstance()
                .id(did)
                .service(List.of(credentialService))
                .build());
    }

    private static MemberParticipant decodeMember(String did) {
        try {
            return ParticipantIdentifierScheme.decodeDid(did) instanceof MemberParticipant member ? member : null;
        } catch (XrdRuntimeException e) {
            return null;
        }
    }

    /**
     * This server, identified by matching the DID's authority against the GlobalConf-registered
     * addresses of the member's instance at the identity hub's DID port.
     */
    private Optional<HostingServer> hostingServer(MemberParticipant member) {
        var didPort = port(IH_DID);
        if (didPort.isEmpty()) {
            return Optional.empty();
        }
        return globalConfProvider.getSecurityServers(member.member().getXRoadInstance()).stream()
                .map(id -> new HostingServer(id, globalConfProvider.getSecurityServerAddress(id)))
                .filter(server -> server.address() != null
                        && DspConventions.didAuthority(server.address(), didPort.getAsInt()).equals(member.ssHost()))
                .findFirst();
    }

    /** Whether the member itself or any of its subsystems is a client of {@code server} in GlobalConf. */
    private boolean isRegisteredClient(ClientId member, SecurityServerId server) {
        return globalConfProvider.getMembers(member.getXRoadInstance()).stream()
                .map(info -> info.id())
                .filter(client -> member.equals(client.getMemberId()))
                .anyMatch(client -> globalConfProvider.isSecurityServerClient(client, server));
    }

    private OptionalInt port(String apiContext) {
        return portMappingRegistry.getAll().stream()
                .filter(mapping -> apiContext.equals(mapping.name()))
                .mapToInt(mapping -> mapping.port())
                .findFirst();
    }

    private record HostingServer(SecurityServerId id, String address) { }
}
