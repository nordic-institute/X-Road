package ee.cyber.xroad.mediator.message;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.message.SoapUtils;

/**
 * Represents the X-Road 6.0 header with the optional addition of legacy headers.
 */
@Getter
@Setter
@ToString(callSuper = true)
@XmlRootElement(name = "Header", namespace = SoapUtils.NS_SOAPENV)
@XmlAccessorType(XmlAccessType.FIELD)
public class XroadSoapHeader extends SoapHeader {

    /**
     * This field contains the legacy X-Road headers when converting X-Road 5.0
     * SOAP message to X-Road 6.0 SOAP message.
     */
    @XmlElement(name = "xroadHeader", required = false, namespace = NS_XROAD)
    private V5XRoadHeaderFields xroadHeader;

}
