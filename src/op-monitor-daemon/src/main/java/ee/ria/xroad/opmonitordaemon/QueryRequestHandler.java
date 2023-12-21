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
package ee.ria.xroad.opmonitordaemon;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.message.JaxbUtils;
import ee.ria.xroad.common.message.SoapMessageEncoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.util.JsonUtils;
import ee.ria.xroad.common.util.ResourceUtils;
import ee.ria.xroad.opmonitordaemon.message.ObjectFactory;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.sun.istack.ByteArrayDataSource;
import jakarta.activation.DataHandler;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.UnmarshalException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.ValidationEvent;
import jakarta.xml.bind.attachment.AttachmentMarshaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jaxb.runtime.api.AccessorException;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

import static ee.ria.xroad.common.ErrorCodes.CLIENT_X;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CONTENT_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CONTENT_TRANSFER_ENCODING;

/**
 * Base class for operational daemon query request handlers.
 */
@Slf4j
abstract class QueryRequestHandler {

    static final ObjectFactory OBJECT_FACTORY = new ObjectFactory();
    static final ObjectWriter OBJECT_WRITER = JsonUtils.getObjectWriter();

    private static final JAXBContext JAXB_CTX = initJaxbCtx();
    private static final Schema OP_MONITORING_SCHEMA = createSchema();

    /**
     * Handle the given request and write the response in the provided output
     * stream.
     * @param requestSoap the request SOAP message
     * @param out the output stream for writing the response
     * @param contentTypeCallback function to call when response content type
     * is available (before writing to output stream)
     */
    abstract void handle(SoapMessageImpl requestSoap, OutputStream out,
            Consumer<String> contentTypeCallback) throws Exception;

    private static JAXBContext initJaxbCtx() {
        try {
            return JAXBContext.newInstance(ObjectFactory.class);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("java:S2755")
    private static Schema createSchema() {
        try {
            final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,jar:file");
            return factory.newSchema(ResourceUtils.getClasspathResource("op-monitoring.xsd"));
        } catch (SAXException e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }
    }

    private static Unmarshaller createUnmarshaller(Class<?> clazz)
            throws Exception {
        Unmarshaller unmarshaller = JaxbUtils.createUnmarshaller(clazz);
        unmarshaller.setEventHandler(QueryRequestHandler::validationFailed);
        unmarshaller.setSchema(OP_MONITORING_SCHEMA);

        return unmarshaller;
    }

    private static boolean validationFailed(ValidationEvent event) {
        if (event.getSeverity() == ValidationEvent.FATAL_ERROR) {
            return false;
        }

        if (event.getSeverity() == ValidationEvent.ERROR
                && event.getLinkedException() instanceof AccessorException
                && event.getLinkedException().getCause()
                instanceof CodedException) {
            return false;
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    static <T> T getRequestData(SoapMessageImpl requestSoap,
            Class<?> clazz) throws Exception {
        Unmarshaller unmarshaller = createUnmarshaller(clazz);

        try {
            return (T) unmarshaller.unmarshal(SoapUtils.getFirstChild(
                    requestSoap.getSoap().getSOAPBody()), clazz).getValue();
        } catch (UnmarshalException e) {
            throw new CodedException(X_INVALID_REQUEST, e.getLinkedException())
                    .withPrefix(CLIENT_X);
        }
    }

    static SoapMessageImpl createResponse(
            SoapMessageImpl requestMessage, JAXBElement<?> jaxbElement)
                    throws Exception {
        return createResponse(requestMessage, createMarshaller(), jaxbElement);
    }

    static SoapMessageImpl createResponse(
            SoapMessageImpl requestMessage, Marshaller marshaller,
            JAXBElement<?> jaxbElement) throws Exception {
        return SoapUtils.toResponse(requestMessage,
                soap -> {
                    soap.getSOAPBody().removeContents();
                    marshaller.marshal(jaxbElement, soap.getSOAPBody());
                });
    }

    private static Marshaller createMarshaller() throws Exception {
        return createMarshaller(null);
    }

    static Marshaller createMarshaller(
            AttachmentMarshaller attachmentMarshaller) throws Exception {
        Marshaller marshaller = JAXB_CTX.createMarshaller();

        marshaller.setAttachmentMarshaller(attachmentMarshaller);
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);

        return marshaller;
    }

    static DataHandler createAttachmentDataSource(
            byte[] payload, String cid, String contentType) {
        return new DataHandler(new ByteArrayDataSource(payload, contentType)) {
            @Override
            public String getName() {
                return cid;
            }
        };
    }

    static byte[] compress(String data) throws IOException {
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(
                dataBytes.length)) {
            GZIPOutputStream gzip = new GZIPOutputStream(bos);
            gzip.write(dataBytes);
            gzip.close();

            return bos.toByteArray();
        }
    }

    private static Map<String, String> getAdditionalAttachmentHeaders(
            String cid) {
        Map<String, String> additionalHeaders = new HashMap<>();

        additionalHeaders.put(HEADER_CONTENT_TRANSFER_ENCODING, "binary");
        additionalHeaders.put(HEADER_CONTENT_ID, "<" + cid + ">");

        return additionalHeaders;
    }

    @RequiredArgsConstructor
    protected static final class SoapEncoderAttachmentMarshaller
            extends AttachmentMarshaller {
        private static final String CID_PREFIX = "cid:";

        private final SoapMessageEncoder responseEncoder;

        private final Map<String, DataHandler> attachments = new HashMap<>();

        void encodeAttachments() throws Exception {
            for (Map.Entry<String, DataHandler> attach : attachments.entrySet()) {
                responseEncoder.attachment(attach.getValue().getContentType(),
                        attach.getValue().getInputStream(),
                        getAdditionalAttachmentHeaders(attach.getKey()));
            }
        }

        @Override
        public String addSwaRefAttachment(DataHandler data) {
            String name = data.getName();

            if (StringUtils.isEmpty(name)) {
                name = UUID.randomUUID().toString();
            }

            attachments.put(name, data);

            return CID_PREFIX + name;
        }

        @Override
        public String addMtomAttachment(DataHandler data,
                String elementNamespace, String elementLocalName) {
            // not using MTOM attachments
            return null;
        }

        @Override
        public String addMtomAttachment(byte[] data, int offset, int length,
                String mimeType, String elementNamespace,
                String elementLocalName) {
            // not using MTOM attachments
            return null;
        }
    }
}
