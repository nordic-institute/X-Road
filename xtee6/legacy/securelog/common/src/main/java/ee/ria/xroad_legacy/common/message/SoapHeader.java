package ee.ria.xroad_legacy.common.message;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

/**
 * This class represents XROAD SOAP message header.
 *
 */
@Getter
@Setter
@ToString
@XmlRootElement(name = "Header", namespace = SoapUtils.NS_SOAPENV)
@XmlAccessorType(XmlAccessType.FIELD)
public class SoapHeader {

    public static final String NS_XROAD = "http://x-road.eu/xsd/xroad_legacy.xsd";
    public static final String PREFIX_XROAD = "xroad";

    @CheckConsistency
    @XmlElement(name = "client", required = true, namespace = NS_XROAD)
    private ClientId client;

    @CheckConsistency
    @XmlElement(name = "service", required = true, namespace = NS_XROAD)
    private ServiceId service;

    @CheckConsistency
    @XmlElement(name = "userId", required = true, namespace = NS_XROAD)
    private String userId;

    @CheckConsistency
    @XmlElement(name = "id", required = true, namespace = NS_XROAD)
    private String queryId;

    @XmlElement(name = "async", required = false, namespace = NS_XROAD)
    private boolean async;

}
