package ee.cyber.sdsb.common.message;

import javax.xml.soap.SOAPFault;

import org.apache.commons.lang3.StringEscapeUtils;

import ee.cyber.sdsb.common.CodedException;

public class SoapFault implements Soap {

    static final String SOAP_NS_SOAP_ENV =
            "http://schemas.xmlsoap.org/soap/envelope/";

    private final String faultCode;
    private final String faultString;
    private final String faultActor;
    private final String faultDetail;

    public SoapFault(SOAPFault soapFault) {
        this.faultCode = soapFault.getFaultCode();
        this.faultString = soapFault.getFaultString();
        this.faultActor = soapFault.getFaultActor();
        this.faultDetail = getFaultDetail(soapFault);
    }

    public String getCode() {
        return faultCode;
    }

    public String getString() {
        return faultString;
    }

    public String getActor() {
        return faultActor;
    }

    public String getDetail() {
        return faultDetail;
    }

    public CodedException toCodedException() {
        return CodedException.fromFault(faultCode, faultString, faultActor,
                faultDetail);
    }

    @Override
    public String getXml() {
        return createFaultXml(faultCode, faultString, faultActor, faultDetail);
    }

    /** Creates SOAP fault message from exception. */
    public static String createFaultXml(CodedException ex) {
        return createFaultXml(ex.getFaultCode(), ex.getFaultString(),
                ex.getFaultActor(), ex.getFaultDetail());
    }

    /** Creates a SOAP fault message. */
    public static String createFaultXml(String faultCode, String faultString,
            String faultActor, String detail) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<SOAP-ENV:Envelope " +
                    "xmlns:SOAP-ENV=\"" + SOAP_NS_SOAP_ENV + "\" " +
                    "xmlns:sdsb=\"" + SoapHeader.NS_SDSB + "\">" +
                    "<SOAP-ENV:Body>" +
                        "<SOAP-ENV:Fault>" +
                            "<faultcode>" + faultCode + "</faultcode>" +
                            "<faultstring>" +
                            StringEscapeUtils.escapeXml(faultString) +
                            "</faultstring>" +
                            (faultActor != null
                             ? ("<faultactor>" +
                                 StringEscapeUtils.escapeXml(faultActor) +
                                 "</faultactor>") : "") +
                            (detail != null
                             ? ("<detail><sdsb:faultDetail>" +
                                 StringEscapeUtils.escapeXml(detail) +
                                 "</sdsb:faultDetail>" + "</detail>") : "") +
                        "</SOAP-ENV:Fault>" +
                    "</SOAP-ENV:Body>" +
                "</SOAP-ENV:Envelope>";
    }

    private static String getFaultDetail(SOAPFault soapFault) {
        if (soapFault.getDetail() != null
                && soapFault.getDetail().getFirstChild() != null) {
            return soapFault.getDetail().getFirstChild().getTextContent();
        }

        return null;
    }

}
