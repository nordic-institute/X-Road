package ee.ria.xroad.common.conf.serverconf.dao;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Hibernate;
import org.hibernate.Query;
import org.hibernate.Session;

import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

/**
 * Service data access object implementation.
 */
public class ServiceDAOImpl extends AbstractDAOImpl<ServiceType> {

    /**
     * Returns the service object for the given service identifier or null
     * if the service cannot be found.
     * @param session the session
     * @param id the service identifier
     * @return the service object
     */
    public ServiceType getService(Session session, ServiceId id) {
        ServiceType serviceType = find(session, id);
        if (serviceType != null) {
            Hibernate.initialize(serviceType.getRequiredSecurityCategory());
        }

        return serviceType;
    }

    /**
     * Returns true, if service with the specified identifier exists.
     * @param session the session
     * @param id the service identifier
     * @return true, if service with the specified identifier exists
     */
    public boolean serviceExists(Session session, ServiceId id) {
        return find(session, id) != null;
    }

    /**
     * Returns services of the specified service provider.
     * @param session the session
     * @param serviceProvider the service provider
     * @return services of the specified service provider
     */
    public List<ServiceId> getServices(Session session,
            ClientId serviceProvider) {
        StringBuilder qb = new StringBuilder();
        qb.append("select s from ServiceType s");
        qb.append(" inner join fetch s.wsdl w");
        qb.append(" inner join fetch w.client c");

        qb.append(" where c.identifier.xRoadInstance = :clientInstance ");
        qb.append(" and c.identifier.memberClass = :clientClass");
        qb.append(" and c.identifier.memberCode = :clientCode");
        qb.append(" and c.identifier.subsystemCode "
                + nullOrName(serviceProvider.getSubsystemCode(),
                        "clientSubsystemCode"));

        Query q = session.createQuery(qb.toString());

        q.setString("clientInstance", serviceProvider.getXRoadInstance());
        q.setString("clientClass", serviceProvider.getMemberClass());
        q.setString("clientCode", serviceProvider.getMemberCode());
        setString(q, "clientSubsystemCode", serviceProvider.getSubsystemCode());

        List<ServiceId> services = new ArrayList<>();
        for (ServiceType service : findMany(q)) {
            services.add(ServiceId.create(serviceProvider,
                    service.getServiceCode(), service.getServiceVersion()));
        }

        return services;
    }

    private ServiceType find(Session session, ServiceId id) {
        StringBuilder qb = new StringBuilder();
        qb.append("select s from ServiceType s");
        qb.append(" inner join fetch s.wsdl w");
        qb.append(" inner join fetch w.client c");

        qb.append(" where s.serviceCode = :serviceCode");
        qb.append(" and s.serviceVersion "
                + nullOrName(id.getServiceVersion(), "serviceVersion"));

        qb.append(" and c.identifier.xRoadInstance = :clientInstance");
        qb.append(" and c.identifier.memberClass = :clientClass");
        qb.append(" and c.identifier.memberCode = :clientCode");
        qb.append(" and c.identifier.subsystemCode "
                + nullOrName(id.getClientId().getSubsystemCode(),
                        "clientSubsystemCode"));

        Query q = session.createQuery(qb.toString());

        q.setString("serviceCode", id.getServiceCode());
        setString(q, "serviceVersion", id.getServiceVersion());

        q.setString("clientInstance", id.getClientId().getXRoadInstance());
        q.setString("clientClass", id.getClientId().getMemberClass());
        q.setString("clientCode", id.getClientId().getMemberCode());
        setString(q, "clientSubsystemCode",
                id.getClientId().getSubsystemCode());

        return findOne(q);
    }
}
