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
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.metadata.ClientListType;
import ee.ria.xroad.common.metadata.ClientType;
import ee.ria.xroad.common.metadata.ObjectFactory;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.common.collect.Iterators;
import com.google.common.collect.Streams;
import com.google.common.net.MediaType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.metadata.MetadataRequests.LIST_CLIENTS;

/**
 * Soap metadata client request processor
 */
@Slf4j
class MetadataClientRequestProcessor extends MessageProcessorBase {

    static final String PARAM_INSTANCE_IDENTIFIER = "xRoadInstance";

    static final JAXBContext JAXB_CTX = initJaxbCtx();
    static final ObjectFactory OBJECT_FACTORY = new ObjectFactory();

    static final ObjectMapper MAPPER;

    static {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        MAPPER = mapper;
    }

    private final String target;

    MetadataClientRequestProcessor(String target,
            HttpServletRequest request, HttpServletResponse response) {
        super(request, response, null);

        this.target = target;
    }

    public boolean canProcess() {
        switch (target) {
            case LIST_CLIENTS: // $FALL-THROUGH$
                return true;
            default:
                return false;
        }
    }

    @Override
    public void process() throws Exception {
        switch (target) {
            case LIST_CLIENTS:
                handleListClients();
                return;
            default: // to nothing
                break;
        }
    }

    private void handleListClients() throws Exception {
        log.trace("handleListClients()");

        String instanceIdentifier = getInstanceIdentifierFromRequest();

        ClientListType list = OBJECT_FACTORY.createClientListType();
        list.getMember().addAll(
                GlobalConf.getMembers(instanceIdentifier).stream().map(m -> {
                    ClientType client = OBJECT_FACTORY.createClientType();
                    client.setId(m.getId());
                    client.setName(m.getName());
                    return client;
                }).collect(Collectors.toList()));

        if (acceptsJson()) {
            writeResponseJson(list);
        } else {
            writeResponseXml(OBJECT_FACTORY.createClientList(list));
        }
    }

    private boolean acceptsJson() {
        return acceptsJson(servletRequest.getHeaders("Accept"));
    }

    private void writeResponseXml(Object object) throws Exception {
        servletResponse.setContentType(MimeTypes.TEXT_XML_UTF8);
        marshal(object, servletResponse.getOutputStream());
    }

    private void writeResponseJson(Object object) throws Exception {
        servletResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        servletResponse.setContentType(
                MimeUtils.contentTypeWithCharset(MimeTypes.JSON, StandardCharsets.UTF_8.name().toLowerCase()));
        MAPPER.writeValue(servletResponse.getOutputStream(), object);
    }

    private String getInstanceIdentifierFromRequest() {
        String instanceIdentifier =
                servletRequest.getParameter(PARAM_INSTANCE_IDENTIFIER);
        if (StringUtils.isBlank(instanceIdentifier)) {
            instanceIdentifier = GlobalConf.getInstanceIdentifier();
        }

        return instanceIdentifier;
    }

    /**
     * Parses the HTTP "Accept" header, checks if it contains application/json media type.
     *
     * Note. Possible media type parameters are ignored since application/json does not define any.
     * Also the quality (q) parameter is ignored, meaning that
     * <pre>Accept: text/xml;q=1.0, application/json;q=0.9</pre>
     * is wrongly interpreted as a request for JSON although the client would prefer XML (assumed to be uncommon).
     */
    static boolean acceptsJson(final Enumeration<String> accept) {
        return accept != null && Streams.stream(Iterators.forEnumeration(accept))
                .flatMap(s -> Arrays.asList(s.split("\\s*,\\s*")).stream())
                .map(MediaType::parse)
                .filter(m -> APPLICATION_JSON.equals(m.withoutParameters()))
                .findAny()
                .isPresent();
    }

    private static final MediaType APPLICATION_JSON = MediaType.JSON_UTF_8.withoutParameters();

    private static void marshal(Object object, OutputStream out)
            throws Exception {
        Marshaller marshaller = JAXB_CTX.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.marshal(object, out);
    }

    private static JAXBContext initJaxbCtx() {
        try {
            return JAXBContext.newInstance(ObjectFactory.class);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

}
