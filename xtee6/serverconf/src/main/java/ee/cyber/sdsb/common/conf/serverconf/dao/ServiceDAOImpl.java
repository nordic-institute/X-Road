package ee.cyber.sdsb.common.conf.serverconf.dao;

import java.util.ArrayList;
import java.util.List;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.hibernate.Hibernate;
import org.hibernate.Query;
import org.hibernate.Session;

import ee.cyber.sdsb.common.conf.serverconf.model.ServiceType;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ServiceDAOImpl extends AbstractDAOImpl<ServiceType> {

    private static final ServiceDAOImpl instance = new ServiceDAOImpl();

    public static ServiceDAOImpl getInstance() {
        return instance;
    }

    public ServiceType getService(Session session, ServiceId id)
            throws Exception {
        ServiceType serviceType = find(session, id);
        if (serviceType != null) {
            Hibernate.initialize(serviceType.getRequiredSecurityCategory());
        }

        return serviceType;
    }

    public boolean serviceExists(Session session, ServiceId id)
            throws Exception {
        return find(session, id) != null;
    }

    public List<ServiceId> getServices(Session session,
            ClientId serviceProvider) {
        StringBuilder qb = new StringBuilder();
        qb.append("select s from ServiceType s");
        qb.append(" inner join fetch s.wsdl w");
        qb.append(" inner join fetch w.client c");

        qb.append(" where c.identifier.sdsbInstance = :clientInstance ");
        qb.append(" and c.identifier.memberClass = :clientClass");
        qb.append(" and c.identifier.memberCode = :clientCode");
        qb.append(" and c.identifier.subsystemCode "
                + nullOrName(serviceProvider.getSubsystemCode(),
                        "clientSubsystemCode"));

        Query q = session.createQuery(qb.toString());

        q.setString("clientInstance", serviceProvider.getSdsbInstance());
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

        qb.append(" and c.identifier.sdsbInstance = :clientInstance");
        qb.append(" and c.identifier.memberClass = :clientClass");
        qb.append(" and c.identifier.memberCode = :clientCode");
        qb.append(" and c.identifier.subsystemCode "
                + nullOrName(id.getClientId().getSubsystemCode(),
                        "clientSubsystemCode"));

        Query q = session.createQuery(qb.toString());

        q.setString("serviceCode", id.getServiceCode());
        setString(q, "serviceVersion", id.getServiceVersion());

        q.setString("clientInstance", id.getClientId().getSdsbInstance());
        q.setString("clientClass", id.getClientId().getMemberClass());
        q.setString("clientCode", id.getClientId().getMemberCode());
        setString(q, "clientSubsystemCode",
                id.getClientId().getSubsystemCode());

        return findOne(q);
    }
}
