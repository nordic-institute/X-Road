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
package org.niis.xroad.ds.issuance.identity;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.model.CredentialUsage;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.store.CredentialStore;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;
import org.eclipse.edc.web.spi.exception.ObjectConflictException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.function.Supplier;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.edc.issuerservice.spi.credentials.statuslist.StatusListCredentialUrl.createUrl;
import static org.eclipse.edc.issuerservice.spi.credentials.statuslist.StatusListCredentialUrl.extractIdFromUrl;
import static org.niis.xroad.ds.issuance.identity.XRoadStatusListCredentialPublisherExtension.NAME;

/**
 * Serves status list (revocation list) credentials with the same HTTP shape as EDC's own {@code
 * StatusListCredentialController}, except the base URL it builds the lookup credential ID from is the bound
 * statuslist base, read live from a {@link BoundIssuerIdentityHolder} on every request instead of a base URL
 * fixed at boot. This matters because {@link XRoadStatusListCredentialPublisher} mints every list under that
 * same live base, which can differ from {@code edc.statuslist.callback.address} (the setting EDC's own
 * controller would have used). While unbound, every request is refused with 503 instead of a lookup under a
 * wrong or missing base.
 */
@Path("{any:.*}")
@RequiredArgsConstructor
public class XRoadStatusListCredentialController {

    private static final List<String> SUPPORTED_TYPES = List.of(APPLICATION_JSON, "application/vc+jwt", MediaType.WILDCARD);

    private final CredentialStore store;
    private final Monitor monitor;
    private final Supplier<ObjectMapper> mapper;
    private final BoundIssuerIdentityHolder holder;

    @GET
    public Response resolveStatusListCredential(@HeaderParam("Accept") @DefaultValue(MediaType.WILDCARD) String acceptHeader,
            @Context ContainerRequestContext context) {

        if (!SUPPORTED_TYPES.contains(acceptHeader)) {
            return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE)
                    .entity("Supported media types are: %s".formatted(SUPPORTED_TYPES))
                    .build();
        }

        var identity = holder.identity();
        if (identity.isEmpty()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(NAME + ": the issuer identity is not bound")
                    .build();
        }

        var httpUrl = context.getUriInfo().getAbsolutePath();
        // status list credentials are identified by their public URL, which differs from the request URL behind a proxy
        var credentialId = createUrl(identity.get().statuslistBaseUrl(), extractIdFromUrl(httpUrl));

        var query = QuerySpec.Builder.newInstance()
                .filter(new Criterion("verifiableCredential.credential.id", "=", credentialId))
                .filter(new Criterion("metadata.published", "=", true))
                .filter(new Criterion("usage", "=", CredentialUsage.StatusList.toString()))
                .build();
        var statusListCredential = store.query(query)
                .orElseThrow(InvalidRequestException::new);

        if (statusListCredential.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (statusListCredential.size() > 1) {
            throw new ObjectConflictException("Multiple active status list credentials found for the same ID");
        }

        var selectedCredential = statusListCredential.iterator().next();

        var publicUrl = selectedCredential.getMetadata().get("publicUrl");
        if (!httpUrl.toString().equals(publicUrl)) {
            monitor.warning("The status list credential's public URL property is not equal to the request URL: '%s' <> '%s'"
                    .formatted(publicUrl, httpUrl));
        }
        var body = selectedCredential.getVerifiableCredential().rawVc();

        var contentType = "application/vc+jwt";
        if (acceptHeader.equals(APPLICATION_JSON)) {
            try {
                body = mapper.get().writeValueAsString(selectedCredential.getVerifiableCredential().credential());
                contentType = APPLICATION_JSON;
            } catch (JacksonException e) {
                throw new EdcException(e);
            }
        }

        return Response.ok()
                .header("Content-Type", contentType)
                .entity(body).build();
    }
}
