package ee.cyber.sdsb.common.message;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import ee.cyber.sdsb.common.identifier.CentralServiceId;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;

/**
 * This class represents SDSB SOAP message header.
 *
 */
@Getter
@Setter
@ToString
@XmlRootElement(name = "Header", namespace = SoapUtils.NS_SOAPENV)
@XmlAccessorType(XmlAccessType.FIELD)
public class SoapHeader {

    public static final String NS_SDSB = "http://x-road.eu/xsd/sdsb.xsd";
    public static final String PREFIX_SDSB = "sdsb";

    @CheckConsistency
    @XmlElement(name = "client", required = true, namespace = NS_SDSB)
    private ClientId client;

    @CheckConsistency
    @XmlElement(name = "service", required = false, namespace = NS_SDSB)
    private ServiceId service;

    @XmlElement(name = "centralService", required = false, namespace = NS_SDSB)
    private CentralServiceId centralService;

    @CheckConsistency
    @XmlElement(name = "userId", required = true, namespace = NS_SDSB)
    private String userId;

    @CheckConsistency
    @XmlElement(name = "id", required = true, namespace = NS_SDSB)
    private String queryId;

    @XmlElement(name = "requestHash", required = false, namespace = NS_SDSB)
    private RequestHash requestHash;

    @XmlElement(name = "async", required = false, namespace = NS_SDSB)
    private boolean async;

}
