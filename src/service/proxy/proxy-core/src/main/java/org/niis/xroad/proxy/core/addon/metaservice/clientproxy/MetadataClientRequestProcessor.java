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
package org.niis.xroad.proxy.core.addon.metaservice.clientproxy;

import ee.ria.xroad.common.metadata.ClientListType;
import ee.ria.xroad.common.metadata.ClientType;
import ee.ria.xroad.common.metadata.ObjectFactory;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.Iterators;
import com.google.common.collect.Streams;
import com.google.common.net.MediaType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.MemberInfo;
import org.niis.xroad.proxy.core.util.AddonRequestContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;

import static org.niis.xroad.proxy.core.util.MetadataRequests.LIST_CLIENTS;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@ArchUnitSuppressed("NoVanillaExceptions")
public class MetadataClientRequestProcessor {

    static final String PARAM_INSTANCE_IDENTIFIER = "xRoadInstance";

    static final JAXBContext JAXB_CTX = initJaxbCtx();
    static final ObjectFactory OBJECT_FACTORY = new ObjectFactory();

    static final ObjectMapper MAPPER;

    static {
        MAPPER = JsonMapper.builder()
                .changeDefaultPropertyInclusion(v -> JsonInclude.Value.construct(
                        JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
    }

    private final GlobalConfProvider globalConfProvider;

    public boolean canProcess(String target) {
        return target.equals(LIST_CLIENTS);
    }

    public void process(AddonRequestContext ctx) throws Exception {
        globalConfProvider.verifyValidity();
        if (ctx.target().equals(LIST_CLIENTS)) {
            handleListClients(ctx);
        }
    }

    private void handleListClients(AddonRequestContext ctx) throws Exception {
        log.trace("handleListClients()");

        String instanceIdentifier = getInstanceIdentifierFromRequest(ctx.request());

        ClientListType list = OBJECT_FACTORY.createClientListType();
        globalConfProvider.getMembers(instanceIdentifier).stream()
                .map(this::toDto)
                .forEach(list.getMember()::add);

        if (acceptsJson(ctx.request())) {
            writeResponseJson(ctx.response(), list);
        } else {
            writeResponseXml(ctx.response(), OBJECT_FACTORY.createClientList(list));
        }
    }

    private ClientType toDto(MemberInfo info) {
        var client = OBJECT_FACTORY.createClientType();
        client.setId(info.id());
        client.setName(info.name());
        client.setSubsystemName(info.subsystemName());
        return client;
    }

    private boolean acceptsJson(RequestWrapper request) {
        return acceptsJson(request.getHeaders().getValues("Accept"));
    }

    private void writeResponseXml(ResponseWrapper response, Object object) throws JAXBException {
        response.setContentType(MimeTypes.TEXT_XML_UTF8);
        marshal(object, response.getOutputStream());
    }

    private void writeResponseJson(ResponseWrapper response, Object object) {
        response.setContentType(MimeUtils.contentTypeWithCharset(MimeTypes.JSON,
                StandardCharsets.UTF_8.name().toLowerCase()));
        MAPPER.writeValue(response.getOutputStream(), object);
    }

    private String getInstanceIdentifierFromRequest(RequestWrapper request) throws Exception {
        String instanceIdentifier = request.getParameter(PARAM_INSTANCE_IDENTIFIER);
        if (StringUtils.isBlank(instanceIdentifier)) {
            instanceIdentifier = globalConfProvider.getInstanceIdentifier();
        }

        return instanceIdentifier;
    }

    /**
     * Parses the HTTP "Accept" header, checks if it contains application/json media type.
     * <p>
     * Note. Possible media type parameters are ignored since application/json does not define any.
     * Also the quality (q) parameter is ignored, meaning that
     * <pre>Accept: text/xml;q=1.0, application/json;q=0.9</pre>
     * is wrongly interpreted as a request for JSON although the client would prefer XML (assumed to be uncommon).
     */
    static boolean acceptsJson(final Enumeration<String> accept) {
        return accept != null && Streams.stream(Iterators.forEnumeration(accept))
                .flatMap(s -> Arrays.stream(s.split("\\s*,\\s*")))
                .map(MediaType::parse)
                .anyMatch(m -> APPLICATION_JSON.equals(m.withoutParameters()));
    }

    private static final MediaType APPLICATION_JSON = MediaType.JSON_UTF_8.withoutParameters();

    private static void marshal(Object object, OutputStream out) throws JAXBException {
        Marshaller marshaller = JAXB_CTX.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.marshal(object, out);
    }

    private static JAXBContext initJaxbCtx() {
        try {
            return JAXBContext.newInstance(ObjectFactory.class);
        } catch (JAXBException e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

}
