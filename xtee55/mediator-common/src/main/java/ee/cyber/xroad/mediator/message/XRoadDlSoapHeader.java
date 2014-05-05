package ee.cyber.xroad.mediator.message;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import ee.cyber.sdsb.common.message.SoapUtils;

import static ee.cyber.xroad.mediator.message.XRoadNamespaces.*;

/**
 * This class represents X-Road 5.0 SOAP message (D/L wrapped) headers for each
 * currently supported namespace.
 * The header contains only fields relevant to the mediators.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class XRoadDlSoapHeader {

    private static final String CONSUMER = "consumer";
    private static final String PRODUCER = "producer";
    private static final String SERVICE = "service";
    private static final String QUERY_ID = "id";
    private static final String USER_ID = "userId";
    private static final String ASYNC = "async";

    @Getter
    @Setter(AccessLevel.PROTECTED)
    @ToString
    @XmlRootElement(name = "Header", namespace = SoapUtils.NS_SOAPENV)
    public static class XX extends AbstractXRoadSoapHeader {

        @XmlElement(name = CONSUMER, required = true, namespace = NS_DL_XX)
        private String consumer;

        @XmlElement(name = PRODUCER, required = true, namespace = NS_DL_XX)
        private String producer;

        @XmlElement(name = SERVICE, required = true, namespace = NS_DL_XX)
        private String service;

        @XmlElement(name = QUERY_ID, required = true, namespace = NS_DL_XX)
        private String queryId;

        @XmlElement(name = USER_ID, required = false, namespace = NS_DL_XX)
        private String userId;

        @XmlElement(name = ASYNC, required = false, namespace = NS_DL_XX)
        private boolean async;
    }

    @Getter
    @Setter(AccessLevel.PROTECTED)
    @ToString
    @XmlRootElement(name = "Header", namespace = SoapUtils.NS_SOAPENV)
    public static class EE extends AbstractXRoadSoapHeader {

        @XmlElement(name = CONSUMER, required = true, namespace = NS_DL_EE)
        private String consumer;

        @XmlElement(name = PRODUCER, required = true, namespace = NS_DL_EE)
        private String producer;

        @XmlElement(name = SERVICE, required = true, namespace = NS_DL_EE)
        private String service;

        @XmlElement(name = QUERY_ID, required = true, namespace = NS_DL_EE)
        private String queryId;

        @XmlElement(name = USER_ID, required = false, namespace = NS_DL_EE)
        private String userId;

        @XmlElement(name = ASYNC, required = false, namespace = NS_DL_EE)
        private boolean async;
    }

    @Getter
    @Setter(AccessLevel.PROTECTED)
    @ToString
    @XmlRootElement(name = "Header", namespace = SoapUtils.NS_SOAPENV)
    public static class EU extends AbstractXRoadSoapHeader {

        @XmlElement(name = CONSUMER, required = true, namespace = NS_DL_EU)
        private String consumer;

        @XmlElement(name = PRODUCER, required = true, namespace = NS_DL_EU)
        private String producer;

        @XmlElement(name = SERVICE, required = true, namespace = NS_DL_EU)
        private String service;

        @XmlElement(name = QUERY_ID, required = true, namespace = NS_DL_EU)
        private String queryId;

        @XmlElement(name = USER_ID, required = false, namespace = NS_DL_EU)
        private String userId;

        @XmlElement(name = ASYNC, required = false, namespace = NS_DL_EU)
        private boolean async;
    }
}
