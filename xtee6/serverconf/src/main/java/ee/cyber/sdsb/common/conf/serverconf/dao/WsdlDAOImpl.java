package ee.cyber.sdsb.common.conf.serverconf.dao;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.hibernate.Session;

import ee.cyber.sdsb.common.conf.serverconf.model.ServiceType;
import ee.cyber.sdsb.common.conf.serverconf.model.WsdlType;
import ee.cyber.sdsb.common.identifier.ServiceId;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WsdlDAOImpl extends AbstractDAOImpl<WsdlType> {

    private static final WsdlDAOImpl instance = new WsdlDAOImpl();

    public static WsdlDAOImpl getInstance() {
        return instance;
    }

    public WsdlType getWsdl(Session session, ServiceId id) throws Exception {
        ServiceType service =
                ServiceDAOImpl.getInstance().getService(session, id);
        if (service != null) {
            return service.getWsdl();
        }

        return null;
    }
}
