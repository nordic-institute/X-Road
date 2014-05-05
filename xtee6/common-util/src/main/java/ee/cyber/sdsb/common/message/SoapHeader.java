package ee.cyber.sdsb.common.message;

import java.util.Map;

import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPHeader;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import ee.cyber.sdsb.common.identifier.AbstractServiceId;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.IdentifierXmlNodeParser;

import static ee.cyber.sdsb.common.message.SoapUtils.*;

class SoapHeader {

    static final String FIELD_CLIENT = "client";
    static final String FIELD_SERVICE = "service";
    static final String FIELD_USER_ID = "userId";
    static final String FIELD_QUERY_ID = "id";
    static final String FIELD_DOCUMENT_ID = "issue";
    static final String FIELD_UNIT = "unit";
    static final String FIELD_POSITION = "position";
    static final String FIELD_USERNAME = "userName";
    static final String FIELD_IS_ASYNC = "async";
    static final String FIELD_AUTHENTICATOR = "authenticator";
    static final String FIELD_PAID = "paid";

    final String charset;

    final ClientId client;
    final AbstractServiceId service;

    final String userId;
    final String queryId;
    final String documentId;
    final String unit;
    final String position;
    final String userName;
    final String authenticator;
    final String paid;

    final boolean isAsync;

    SoapHeader(String charset, ClientId client, AbstractServiceId service,
            String userId, String queryId) {
        this.charset = charset;

        checkRequiredField(FIELD_CLIENT, client);
        this.client = client;

        checkRequiredField(FIELD_SERVICE, service);
        this.service = service;

        checkRequiredField(FIELD_USER_ID, userId);
        this.userId = userId;

        checkRequiredField(FIELD_QUERY_ID, queryId);
        this.queryId = queryId;

        documentId = null;
        unit = null;
        position = null;
        userName = null;
        isAsync = false;
        authenticator = null;
        paid = null;
    }

    SoapHeader(String charset, SOAPHeader soapHeader) throws Exception {
        Map<String, SOAPElement> h = SoapUtils.getHeaderElements(soapHeader);

        this.charset = charset;

        client = getClientId(h);
        service = getServiceId(h);

        userId = getRequiredHeaderValue(h, FIELD_USER_ID);
        queryId = getRequiredHeaderValue(h, FIELD_QUERY_ID);
        documentId = getOptionalHeaderValue(h, FIELD_DOCUMENT_ID);
        unit = getOptionalHeaderValue(h, FIELD_UNIT);
        position = getOptionalHeaderValue(h, FIELD_POSITION);
        userName = getOptionalHeaderValue(h, FIELD_USERNAME);
        isAsync = "true".equals(getOptionalHeaderValue(h, FIELD_IS_ASYNC));
        authenticator = getOptionalHeaderValue(h, FIELD_AUTHENTICATOR);
        paid = getOptionalHeaderValue(h, FIELD_PAID);
    }

    static boolean checkConsistency(SoapHeader first, SoapHeader second) {
        // check that the required fields in the headers match
        return first.client.equals(second.client)
                && first.service.equals(second.service)
                && first.userId.equals(second.userId)
                && first.queryId.equals(second.queryId)
                && StringUtils.equals(first.documentId, second.documentId);

    }

    private static ClientId getClientId(Map<String, SOAPElement> header)
            throws Exception {
        SOAPElement clientElement =
                getRequiredHeaderElement(header, FIELD_CLIENT);
        return IdentifierXmlNodeParser.parseClientId(clientElement);
    }

    private static AbstractServiceId getServiceId(
            Map<String, SOAPElement> header) throws Exception {
        SOAPElement serviceElement =
                getRequiredHeaderElement(header, FIELD_SERVICE);
        return IdentifierXmlNodeParser.parseServiceId(serviceElement);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this,
                ToStringStyle.MULTI_LINE_STYLE);
    }
}
