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
package ee.ria.xroad.common;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.util.XmlUtils;

import lombok.Data;
import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.language.DefaultTemplateLexer;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates request data.
 */
public class Request {
    private String template;
    private ClientId client;
    private ServiceId service;
    private String id;

    private List<RequestTag> content;

    // Used in case of multipart templates.
    private String boundary = null;

    /**
     * Constructs a new request with the given template, data and a boundary
     * to use in case of a multipart template.
     * @param template XML template of this request
     * @param client ID of the client that makes this request
     * @param service ID of the service this request is for
     * @param id request ID string
     * @param content list of request tags that should be placed in the body
     * @param boundary boundary to use in case of a multipart template
     */
    public Request(String template, ClientId client, ServiceId service,
            String id, List<RequestTag> content, String boundary) {
        this(template, client, service, id, content);
        this.boundary = boundary;
    }

    /**
     * Constructs a new request with the given template and data.
     * @param template XML template of this request
     * @param client ID of the client that makes this request
     * @param service ID of the service this request is for
     * @param id request ID string
     * @param content list of request tags that should be placed in the body
     */
    public Request(String template, ClientId client, ServiceId service,
            String id, List<RequestTag> content) {
        this.template = template;
        this.client = client;
        this.service = service;
        this.id = id;
        this.content = content;
    }

    /**
     * Populates this requests's template with the encapsulated data and returns
     * it as a string.
     * @return String
     */
    public String toRawContent() {
        StringTemplate stringTemplate = new StringTemplate(template,
                DefaultTemplateLexer.class);

        Map<String, Object> header = new HashMap<>();
        header.put("client", client);
        header.put("service", service);
        header.put("id", id);

        stringTemplate.setAttribute("xroadNamespace", SoapHeader.NS_XROAD);
        stringTemplate.setAttribute("header", header);
        stringTemplate.setAttribute("request", content);
        stringTemplate.setAttribute("boundary", boundary);

        return stringTemplate.toString();
    }

    /**
     * @return String of this request in pretty-printed XML format
     */
    public String toXml() {
        if (StringUtils.isNotBlank(boundary)) {
            throw new RuntimeException(
                    "Cannot turn request into XML where boundary is specified");
        }

        return prettyFormat(toRawContent());
    }

    /**
     * Converts the given SOAP message string to a pretty-printed format.
     * @param soap the SOAP XML to convert
     * @return pretty-printed String of the SOAP XML
     */
    public static String prettyFormat(String soap) {
        try {
            return XmlUtils.prettyPrintXml(soap);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Representing single tag of request content. Assuming non-hierarchical
     * requests in the first place.
     */
    @Data
    public static class RequestTag {
        private String tagName;
        private String value;

        /**
         * Constructs a new request tag with the given tag name and value.
         * @param tagName name of the tag
         * @param value value of the tag
         */
        public RequestTag(String tagName, String value) {
            this.tagName = tagName;
            this.value = value;
        }
    }
}
