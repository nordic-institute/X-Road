package ee.cyber.sdsb.common;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import lombok.Data;

import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.language.DefaultTemplateLexer;
import org.apache.commons.lang3.StringUtils;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.message.SoapHeader;

public class Request {
    private String template;
    private ClientId client;
    private ServiceId service;
    private String id;
    private boolean async = false;

    private List<RequestTag> content;

    // Used in case of multipart templates.
    private String boundary = null;

    public Request(String template, ClientId client, ServiceId service,
            String id, List<RequestTag> content, boolean async,
            String boundary) {
        this(template, client, service, id, content, async);
        this.boundary = boundary;
    }

    public Request(String template, ClientId client, ServiceId service,
            String id, List<RequestTag> content, boolean async) {
        this.template = template;
        this.client = client;
        this.service = service;
        this.id = id;
        this.content = content;
        this.async = async;
    }

    public String toRawContent() {
        StringTemplate stringTemplate = new StringTemplate(template,
                DefaultTemplateLexer.class);

        Map<String, Object> header = new HashMap<>();
        header.put("client", client);
        header.put("service", service);
        header.put("id", id);
        header.put("async", async);

        stringTemplate.setAttribute("sdsbNamespace", SoapHeader.NS_SDSB);
        stringTemplate.setAttribute("header", header);
        stringTemplate.setAttribute("request", content);
        stringTemplate.setAttribute("boundary", boundary);

        return stringTemplate.toString();
    }

    public String toXml() {
        if (StringUtils.isNotBlank(boundary)) {
            throw new RuntimeException(
                    "Cannot turn request into XML where boundary is specified");
        }

        return prettyFormat(toRawContent());
    }

    public static String prettyFormat(String soap) {
        try {
            Source xmlInput = new StreamSource(new StringReader(soap));
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            TransformerFactory transformerFactory = TransformerFactory
                    .newInstance();

            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(
                    "{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(xmlInput, xmlOutput);
            return xmlOutput.getWriter().toString();
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

        public RequestTag(String tagName, String value) {
            this.tagName = tagName;
            this.value = value;
        }
    }
}
