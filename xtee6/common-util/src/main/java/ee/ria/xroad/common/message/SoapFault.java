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

import ee.ria.xroad.common.CodedException;

import javax.xml.namespace.QName;
import javax.xml.soap.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Soap interface implementation representing an error message.
 */
public class SoapFault implements Soap {

    private static final QName FAULT_DETAIL = new QName(SoapHeader.NS_XROAD, "faultDetail", SoapHeader.PREFIX_XROAD);

    private final QName faultCode;
    private final String faultString;
    private final String faultActor;
    private final String faultDetail;

    /**
     * Creates a new SOAP fault from a SOAPFault DOM element.
     *
     * @param soapFault the DOM element used in created of the fault
     */
    public SoapFault(SOAPFault soapFault) {
        this.faultCode = soapFault.getFaultCodeAsQName();
        this.faultString = soapFault.getFaultString();
        this.faultActor = soapFault.getFaultActor();
        this.faultDetail = getFaultDetail(soapFault);
    }

    /**
     * Gets the fault code of the SOAP fault.
     *
     * @return a String with the fault code
     */
    public String getCode() {
        return faultCode.getLocalPart();
    }

    /**
     * @return fault code as QName
     */
    public QName getCodeAsQName() {
        return faultCode;
    }

    /**
     * Gets the fault string of the SOAP fault.
     *
     * @return a String giving an explanation of the fault
     */
    public String getString() {
        return faultString;
    }

    /**
     * Gets the fault actor of the SOAP fault.
     *
     * @return a String giving the actor in the message path that caused this fault
     */
    public String getActor() {
        return faultActor;
    }

    /**
     * Gets the fault detail of the SOAP fault.
     *
     * @return a String with application-specific error information
     */
    public String getDetail() {
        return faultDetail;
    }

    /**
     * Converts this SOAP fault into a coded exception.
     *
     * @return CodedException
     */
    public CodedException toCodedException() {
        return CodedException.fromFault(faultCode, faultString, faultActor, faultDetail);
    }

    @Override
    public String getXml() throws IOException {
        return createFaultXml(faultCode, faultString, faultActor, faultDetail);
    }

    /**
     * Creates SOAP fault message from exception.
     *
     * @param ex exception representing a SOAP fault
     * @return a String containing XML of the SOAP fault represented by the given coded exception
     */
    public static String createFaultXml(CodedException ex) throws IOException {
        return createFaultXml(ex.getFaultCodeAsQName(), ex.getFaultString(), ex.getFaultActor(), ex.getFaultDetail());
    }

    /**
     * Creates a SOAP fault message.
     *
     * @param faultCode   code of the new SOAP fault
     * @param faultString string of the new SOAP fault
     * @param faultActor  actor of the new SOAP fault
     * @param detail      detail of the new SOAP fault
     * @return a String containing XML of the SOAP fault represented by the given parameters
     */
    public static String createFaultXml(QName faultCode, String faultString,
                                        String faultActor, String detail) throws IOException {

        try {
            final SOAPMessage msg = SoapUtils.MESSAGE_FACTORY.createMessage();
            final SOAPFault soapFault = msg.getSOAPBody().addFault();
            soapFault.setFaultCode(faultCode);
            if (faultActor != null) {
                soapFault.setFaultActor(faultActor);
            }
            if (faultString != null) {
                soapFault.setFaultString(faultString);
            }
            if (detail != null) {
                soapFault.addDetail().addChildElement(FAULT_DETAIL).addTextNode(detail);
            }
            return SoapUtils.getXml(msg, StandardCharsets.UTF_8.name());
        } catch (SOAPException | IOException e) {
            //SoapUtils.getXml converts SOAP exceptions to IOExceptions
            throw new IOException("Creating fault xml failed", e);
        }
    }

    /**
     * Creates a SOAP fault message.
     *
     * @see #createFaultXml(QName, String, String, String)
     */
    public static String createFaultXml(String faultCode,
                                        String faultString,
                                        String faultActor,
                                        String detail) throws IOException {
        return createFaultXml(new QName(faultCode), faultString, faultActor, detail);
    }

    private static String getFaultDetail(SOAPFault soapFault) {
        if (soapFault.getDetail() != null
                && soapFault.getDetail().getFirstChild() != null) {
            return soapFault.getDetail().getFirstChild().getTextContent();
        }

        return null;
    }

}
