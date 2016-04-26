/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.message;

import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import ee.ria.xroad.common.util.MimeUtils;

import static ee.ria.xroad.common.identifier.IdentifierXmlNodeParser.NS_IDENTIFIERS;
import static ee.ria.xroad.common.identifier.IdentifierXmlNodeParser.PREFIX_IDENTIFIERS;
import static ee.ria.xroad.common.message.SoapHeader.NS_XROAD;
import static ee.ria.xroad.common.message.SoapHeader.PREFIX_XROAD;
import static ee.ria.xroad.common.message.SoapUtils.*;

/**
 * Builds SOAP messages from the provided header.
 */
@Getter
@Setter
public class SoapBuilder {

    /**
     * Functional interface for the callback used when assembling the SOAP body.
     */
    public interface SoapBodyCallback {
        /**
         * Populates the SOAPBody object with content.
         * @param soapBody the SOAP body that needs to be populated by content.
         * @throws Exception if errors occur when populating the SOAP body
         */
        void create(SOAPBody soapBody) throws Exception;
    }

    private static final Logger LOG =
            LoggerFactory.getLogger(SoapBuilder.class);

    private static final String DEFAULT_CHARSET = MimeUtils.UTF8;

    private String charset = DEFAULT_CHARSET;
    private SoapHeader header;
    private boolean isRpcEncoded;
    private SoapBodyCallback createBodyCallback;

    /**
     * Builds the SOAP message using the currently set header, character set and
     * callback function for populating the SOAP body.
     * @return SoapMessageImpl
     * @throws Exception in case errors occur when creating the SOAP message
     */
    public SoapMessageImpl build() throws Exception {
        if (header == null) {
            throw new IllegalStateException("Header cannot be null");
        }

        RequiredHeaderFieldsChecker.checkRequiredFields(header);
        LOG.trace("build(header: {})", header);

        SOAPMessage soap = createMessage();
        assembleMessage(soap);
        assembleMessageBody(soap);

        String serviceName = getServiceName(soap.getSOAPBody());
        validateServiceName(header.getService().getServiceCode(), serviceName);

        return new SoapMessageImpl(SoapUtils.getBytes(soap), charset, header,
                soap, serviceName);
    }

    protected void addNamespaces(SOAPMessage soapMessage, boolean rpcEncoded)
            throws SOAPException {
        SOAPEnvelope soapEnvelope = soapMessage.getSOAPPart().getEnvelope();
        soapEnvelope.addNamespaceDeclaration(PREFIX_XROAD, NS_XROAD);
        soapEnvelope.addNamespaceDeclaration(
                PREFIX_IDENTIFIERS, NS_IDENTIFIERS);

        if (rpcEncoded) {
            soapEnvelope.addAttribute(new QName(RPC_ATTR), RPC_ENCODING);
        }
    }

    private void assembleMessageBody(SOAPMessage soap) throws Exception {
        SoapBodyCallback cb = createBodyCallback;
        if (cb == null) {
            final String bodyNodeName = header.getService().getServiceCode();
            cb = new SoapBodyCallback() {
                @Override
                public void create(SOAPBody soapBody) throws Exception {
                    Document doc = soapBody.getOwnerDocument();
                    Element node = doc.createElementNS(NS_XROAD,
                            PREFIX_XROAD + ":" + bodyNodeName);
                    soapBody.appendChild(node);
                }
            };
        }

        cb.create(soap.getSOAPBody());
    }

    private void assembleMessage(SOAPMessage soap) throws Exception {
        // Since we are marshaling the header into the SOAPEnvelope object
        // (creating a new SOAPHeader element), we need to remove the existing
        // SOAPHeader element and SOAPBody from the Envelope, then marshal
        // the header and add the body back to get correct order of elements.
        SOAPEnvelope envelope = soap.getSOAPPart().getEnvelope();

        Node soapBody = envelope.removeChild(soap.getSOAPBody());
        envelope.removeContents(); // removes newlines etc.

        Marshaller marshaller = JaxbUtils.createMarshaller(header.getClass(),
                new SoapNamespacePrefixMapper());
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        marshaller.marshal(header, envelope);

        envelope.appendChild(soapBody);
    }

    private SOAPMessage createMessage() throws SOAPException {
        SOAPMessage soap = SoapUtils.MESSAGE_FACTORY.createMessage();
        addNamespaces(soap, isRpcEncoded);
        return soap;
    }

}
