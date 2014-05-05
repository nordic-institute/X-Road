package ee.cyber.sdsb.common;

import java.util.List;
import java.util.Map;

import javax.xml.soap.Detail;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFault;
import javax.xml.soap.SOAPMessage;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.message.SoapUtils;

public class SoapMessageUtils {

    public static String getElementValue(Element e, String tagName) {
        NodeList list = e.getElementsByTagName(tagName);
        if (list.getLength() > 0) {
            return list.item(0).getTextContent();
        }

        throw new CodedException(ErrorCodes.X_INVALID_SOAP,
                "Could not find element '%s'", tagName);
    }

    public static void createElement(SOAPElement parent, String name,
            String value) throws Exception {
        parent.addChildElement(name).setTextContent(value);
    }

    public static SoapMessageImpl toResponse(SoapMessageImpl requestMessage)
            throws Exception {
        SoapMessageImpl responseMessage = SoapUtils.toResponse(requestMessage);

        List<SOAPElement> children = SoapUtils.getChildElements(
                responseMessage.getBody());
        if (children.isEmpty()) {
            throw new CodedException(ErrorCodes.X_INVALID_SOAP,
                    "Missing response element");
        }

        SOAPElement responseElement = children.get(0);
        responseElement.removeContents(); // clear contents of response element

        return responseMessage;
    }

    public static SOAPElement getResponseElement(SoapMessageImpl responseMessage)
            throws Exception {
        SOAPBody responseBody = responseMessage.getBody();

        List<SOAPElement> children = SoapUtils.getChildElements(responseBody);
        if (children.isEmpty()) {
            throw new CodedException(ErrorCodes.X_INVALID_SOAP,
                    "Missing response element");
        }

        return children.get(0);
    }

    public static String getMessageContent(
            Map<String, String> requestData) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : requestData.entrySet()) {
            String fieldName = entry.getKey();
            String fieldRow = String.format("<%s>%s</%s>", fieldName,
                    entry.getValue(), fieldName);
            sb.append(fieldRow);
        }
        return sb.toString();
    }

    public static boolean isFaultResponse(SOAPMessage message)
            throws SOAPException {
        return message.getSOAPBody().getFault() != null;
    }

    public static String[] getFaultCodeAndString(SOAPMessage message)
            throws SOAPException {
        SOAPFault fault = message.getSOAPBody().getFault();
        if (fault == null) {
            return null;
        }

        return new String[] {fault.getFaultCode(), fault.getFaultString(),
                detail(fault.getDetail())};
    }

    static String detail(Detail detail) {
        if (detail != null && detail.getFirstChild() != null) {
            return detail.getFirstChild().getTextContent();
        }

        return "";
    }
}
