package ee.ria.xroad.common.conf.serverconf.dao;

import org.hibernate.Session;

import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.conf.serverconf.model.WsdlType;
import ee.ria.xroad.common.identifier.ServiceId;

/**
 * Wsdl data access object implementation.
 */
public class WsdlDAOImpl extends AbstractDAOImpl<WsdlType> {

    /**
     * Returns the WSDL of the given service identifier.
     * @param session the session
     * @param id the service identifier
     * @return the WSDL of the given service identifier
     */
    public WsdlType getWsdl(Session session, ServiceId id) {
        ServiceType service =
                new ServiceDAOImpl().getService(session, id);
        if (service != null) {
            return service.getWsdl();
        }

        return null;
    }
}
