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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.niis.xroad.ds.identity.DspConventions;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.MemberInfo;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegisteredMemberDidDocumentFilterTest {

    private static final ClientId.Conf MEMBER = ClientId.Conf.create("DEV", "COM", "222");
    private static final ClientId.Conf SUBSYSTEM = ClientId.Conf.create("DEV", "COM", "222", "SUB");
    private static final SecurityServerId.Conf SS_ID = SecurityServerId.Conf.create("DEV", "GOV", "1", "ss0");
    private static final String SS_ADDRESS = "ss0.example.org";
    private static final String MEMBER_DID =
            ParticipantIdentifierScheme.memberDid(MEMBER, DspConventions.didAuthority(SS_ADDRESS)).toString();
    private static final int NO_CONTENT = 204;
    private static final int OK = 200;

    @Mock
    private GlobalConfProvider globalConfProvider;
    @Mock
    private PortMappingRegistry portMappingRegistry;
    @Mock
    private Monitor monitor;
    @Mock
    private ContainerRequestContext request;
    @Mock
    private ContainerResponseContext response;
    @Mock
    private UriInfo uriInfo;

    private final MultivaluedMap<String, Object> responseHeaders = new MultivaluedHashMap<>();

    @BeforeEach
    void setUp() {
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(response.getHeaders()).thenReturn(responseHeaders);
        when(portMappingRegistry.getAll()).thenReturn(List.of(
                new PortMapping("did", 7183, "/"), new PortMapping("credentials", 7185, "/api/credentials")));
        when(globalConfProvider.getSecurityServers("DEV")).thenReturn(List.of(SS_ID));
        when(globalConfProvider.getSecurityServerAddress(SS_ID)).thenReturn(SS_ADDRESS);
        when(globalConfProvider.getMembers("DEV")).thenReturn(List.of(
                new MemberInfo(MEMBER, "Member", null), new MemberInfo(SUBSYSTEM, "Member", "Sub")));
    }

    private RegisteredMemberDidDocumentFilter filter() {
        return new RegisteredMemberDidDocumentFilter(globalConfProvider, portMappingRegistry, monitor);
    }

    private void requestFor(String path) {
        when(uriInfo.getPath()).thenReturn(path);
        when(uriInfo.getAbsolutePath()).thenReturn(URI.create("https://" + SS_ADDRESS + ":7183/" + path));
    }

    private void controllerFoundNoDocument() {
        when(response.getStatus()).thenReturn(NO_CONTENT);
        when(response.hasEntity()).thenReturn(false);
    }

    private DidDocument servedDocument() {
        var entity = ArgumentCaptor.forClass(Object.class);
        verify(response).setStatus(OK);
        verify(response).setEntity(entity.capture(), any(), eq(MediaType.APPLICATION_JSON_TYPE));
        return (DidDocument) entity.getValue();
    }

    @Test
    void registeredMemberWithoutParticipantContextResolvesToAKeylessDocument() {
        requestFor("v1/DEV/COM/222/did.json");
        controllerFoundNoDocument();
        when(globalConfProvider.isSecurityServerClient(SUBSYSTEM, SS_ID)).thenReturn(true);

        filter().filter(request, response);

        var document = servedDocument();
        assertThat(document.getId()).isEqualTo(MEMBER_DID);
        assertThat(document.getVerificationMethod()).isEmpty();
        assertThat(document.getAuthentication()).isEmpty();
        assertThat(document.getService()).singleElement().satisfies(service -> {
            assertThat(service.getType()).isEqualTo("CredentialService");
            assertThat(service.getId()).isEqualTo("DEV:COM:222-credential-service");
            assertThat(service.getServiceEndpoint())
                    .isEqualTo("https://ss0.example.org:7185/api/credentials/v1/participants/DEV:COM:222");
        });
        assertThat(responseHeaders.getFirst("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void memberRegisteredAsAClientItselfResolves() {
        requestFor("v1/DEV/COM/222/did.json");
        controllerFoundNoDocument();
        when(globalConfProvider.isSecurityServerClient(MEMBER, SS_ID)).thenReturn(true);

        filter().filter(request, response);

        assertThat(servedDocument().getId()).isEqualTo(MEMBER_DID);
    }

    @Test
    void memberThatIsNotAClientOfThisServerStaysUnresolved() {
        requestFor("v1/DEV/COM/222/did.json");
        controllerFoundNoDocument();

        filter().filter(request, response);

        assertResponseUntouched();
    }

    @Test
    void provisionedMemberDocumentIsServedAsIs() {
        requestFor("v1/DEV/COM/222/did.json");
        when(response.getStatus()).thenReturn(OK);
        when(response.hasEntity()).thenReturn(true);

        filter().filter(request, response);

        assertResponseUntouched();
        verifyNoInteractions(globalConfProvider);
    }

    @Test
    void emptyResponseOffTheDidDocumentPathIsLeftAlone() {
        requestFor("v1/DEV/COM/222");
        controllerFoundNoDocument();

        filter().filter(request, response);

        assertResponseUntouched();
        verifyNoInteractions(globalConfProvider);
    }

    @Test
    void didOfAnotherServerStaysUnresolved() {
        when(uriInfo.getPath()).thenReturn("v1/DEV/COM/222/did.json");
        when(uriInfo.getAbsolutePath()).thenReturn(URI.create("https://other.example.org:7183/v1/DEV/COM/222/did.json"));
        controllerFoundNoDocument();
        when(globalConfProvider.isSecurityServerClient(any(), any())).thenReturn(true);

        filter().filter(request, response);

        assertResponseUntouched();
    }

    @Test
    void systemAndMalformedDidsStayUnresolved() {
        controllerFoundNoDocument();
        when(globalConfProvider.isSecurityServerClient(any(), any())).thenReturn(true);

        requestFor("v1/system/did.json");
        filter().filter(request, response);
        requestFor("v9/DEV/COM/222/did.json");
        filter().filter(request, response);
        requestFor("did.json");
        filter().filter(request, response);

        assertResponseUntouched();
    }

    @Test
    void globalConfFailureLeavesTheResponseUntouched() {
        requestFor("v1/DEV/COM/222/did.json");
        controllerFoundNoDocument();
        when(globalConfProvider.getSecurityServers("DEV")).thenThrow(new IllegalStateException("globalconf expired"));

        filter().filter(request, response);

        assertResponseUntouched();
        verify(monitor).warning(any(String.class), any(Throwable.class));
    }

    private void assertResponseUntouched() {
        verify(response, never()).setStatus(anyInt());
        verify(response, never()).setEntity(any(), any(), any());
        assertThat(responseHeaders).isEmpty();
    }
}
