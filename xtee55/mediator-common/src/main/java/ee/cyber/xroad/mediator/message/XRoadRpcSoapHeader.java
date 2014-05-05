package ee.cyber.xroad.mediator.message;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import ee.cyber.sdsb.common.message.SoapUtils;

import static ee.cyber.xroad.mediator.message.XRoadNamespaces.NS_RPC;

/**
 * This class represents X-Road 5.0 SOAP message (RPC encoded) legacy header.
 * The header contains only fields relevant to the mediators.
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@ToString
@XmlRootElement(name = "Header", namespace = SoapUtils.NS_SOAPENV)
public class XRoadRpcSoapHeader extends AbstractXRoadSoapHeader {

    @XmlElement(name = "asutus", required = true, namespace = NS_RPC)
    private String consumer;

    @XmlElement(name = "andmekogu", required = true, namespace = NS_RPC)
    private String producer;

    @XmlElement(name = "nimi", required = true, namespace = NS_RPC)
    private String service;

    @XmlElement(name = "id", required = true, namespace = NS_RPC)
    private String queryId;

    @XmlElement(name = "isikukood", required = false, namespace = NS_RPC)
    private String userId;

    @XmlElement(name = "asynkroonne", required = false, namespace = NS_RPC)
    private boolean async;

}
