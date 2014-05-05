package ee.cyber.xroad.mediator.message;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import ee.cyber.sdsb.common.message.SoapHeader;
import ee.cyber.sdsb.common.message.SoapUtils;

@Getter
@Setter
@ToString(callSuper = true)
@XmlRootElement(name = "Header", namespace = SoapUtils.NS_SOAPENV)
@XmlAccessorType(XmlAccessType.FIELD)
public class SdsbSoapHeader extends SoapHeader {

    /**
     * This field contains the legacy X-Road headers when converting X-Road 5.0
     * SOAP message to SDSB SOAP message.
     */
    @XmlElement(name = "xroadHeader", required = false, namespace = NS_SDSB)
    private XRoadHeaderFields xroadHeader;

}
