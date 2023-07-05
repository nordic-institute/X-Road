/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.conf.serverconf.dao;

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.query.Query;

import javax.persistence.Tuple;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;

/**
 * Service data access object implementation.
 */
@Slf4j
public class ServiceDAOImpl extends AbstractDAOImpl<ServiceType> {

    private static final String CLIENT_SUBSYSTEM_CODE = "clientSubsystemCode";

    /**
     * Returns the service object for the given service identifier or null
     * if the service cannot be found.
     * @param session the session
     * @param id the service identifier
     * @return the service object
     */
    public ServiceType getService(Session session, ServiceId id) {
        return find(session, id);
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
    public List<ServiceId.Conf> getServices(Session session, ClientId serviceProvider) {
        return getServicesByDescriptionType(session, serviceProvider);
    }

    /**
     * Returns the services of the specified service provider filtered by type.
     * @param session the session
     * @param serviceProvider the service provider
     * @param descriptionType filter results by description type
     * @return services of the specified service provider
     */
    @SuppressWarnings("squid:S1192")
    public List<ServiceId.Conf> getServicesByDescriptionType(Session session,
            ClientId serviceProvider, DescriptionType... descriptionType) {
        CriteriaBuilder builder = session.getCriteriaBuilder();
        CriteriaQuery<Tuple> tq = builder.createTupleQuery();
        Root<ServiceType> root = tq.from(ServiceType.class);
        Join<ServiceType, ServiceDescriptionType> joinServiceDescription = root.join("serviceDescription");
        Join<ServiceDescriptionType, ClientType> joinClient = joinServiceDescription.join("client");
        tq.multiselect(root.get("serviceCode"), root.get("serviceVersion"));
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.equal(joinClient.get("identifier").<String>get("xRoadInstance"),
                serviceProvider.getXRoadInstance()));
        predicates.add(builder.equal(joinClient.get("identifier").<String>get("memberClass"),
                serviceProvider.getMemberClass()));
        predicates.add(builder.equal(joinClient.get("identifier").<String>get("memberCode"),
                serviceProvider.getMemberCode()));
        if (serviceProvider.getSubsystemCode() == null) {
            predicates.add(builder.isNull(joinClient.get("identifier").<String>get("subsystemCode")));
        } else {
            predicates.add(builder.equal(joinClient.get("identifier").<String>get("subsystemCode"),
                    serviceProvider.getSubsystemCode()));
        }
        if (descriptionType != null && descriptionType.length > 0) {
            predicates.add(joinServiceDescription.get("type").in((Object[]) descriptionType));
        }
        tq.where(predicates.toArray(new Predicate[] {}));
        List<Tuple> resultList = session.createQuery(tq).getResultList();
        List<ServiceId.Conf> services = new ArrayList<>();
        for (Tuple tuple : resultList) {
            services.add(ServiceId.Conf.create(serviceProvider, (String) tuple.get(0),
                    (String) tuple.get(1)));
        }
        return services;
    }

    @SuppressWarnings("squid:S1192")
    private ServiceType find(Session session, ServiceId id) {
        StringBuilder qb = new StringBuilder();
        qb.append("select s from ServiceType s");
        qb.append(" inner join fetch s.serviceDescription w");
        qb.append(" inner join fetch w.client c");

        qb.append(" where s.serviceCode = :serviceCode");
        qb.append(" and s.serviceVersion "
                + nullOrName(id.getServiceVersion(), "serviceVersion"));

        qb.append(" and c.identifier.xRoadInstance = :clientInstance");
        qb.append(" and c.identifier.memberClass = :clientClass");
        qb.append(" and c.identifier.memberCode = :clientCode");
        qb.append(" and c.identifier.subsystemCode "
                + nullOrName(id.getClientId().getSubsystemCode(),
                CLIENT_SUBSYSTEM_CODE));

        Query<ServiceType> q = session.createQuery(qb.toString(), ServiceType.class);

        q.setParameter("serviceCode", id.getServiceCode());
        setString(q, "serviceVersion", id.getServiceVersion());
        q.setParameter("clientInstance", id.getClientId().getXRoadInstance());
        q.setParameter("clientClass", id.getClientId().getMemberClass());
        q.setParameter("clientCode", id.getClientId().getMemberCode());
        setString(q, CLIENT_SUBSYSTEM_CODE, id.getClientId().getSubsystemCode());

        return findOne(q);
    }
}
