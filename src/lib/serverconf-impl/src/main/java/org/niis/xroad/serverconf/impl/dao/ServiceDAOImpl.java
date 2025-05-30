/*
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
package org.niis.xroad.serverconf.impl.dao;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.ServiceDescriptionEntity;
import org.niis.xroad.serverconf.impl.entity.ServiceEntity;
import org.niis.xroad.serverconf.impl.entity.ServiceIdEntity;
import org.niis.xroad.serverconf.model.DescriptionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Service data access object implementation.
 */
@Slf4j
public class ServiceDAOImpl extends AbstractDAOImpl<ServiceEntity> {

    private static final String CLIENT_SUBSYSTEM_CODE = "clientSubsystemCode";

    /**
     * Returns the service object for the given service identifier or null
     * if the service cannot be found.
     * @param session the session
     * @param id the service identifier
     * @return the service object
     */
    public ServiceEntity getService(Session session, ServiceId id) {
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
     * @param serviceProviderId the service provider
     * @return services of the specified service provider
     */
    public List<ServiceIdEntity> getServices(Session session, ClientId serviceProviderId) {
        return getServicesByDescriptionType(session, serviceProviderId);
    }

    /**
     * Returns the services of the specified service provider filtered by type.
     * @param session the session
     * @param serviceProviderId the service provider
     * @param descriptionType filter results by description type
     * @return services of the specified service provider
     */
    @SuppressWarnings("squid:S1192")
    public List<ServiceIdEntity> getServicesByDescriptionType(Session session,
                                                              ClientId serviceProviderId, DescriptionType... descriptionType) {
        CriteriaBuilder builder = session.getCriteriaBuilder();
        CriteriaQuery<Tuple> tq = builder.createTupleQuery();
        Root<ServiceEntity> root = tq.from(ServiceEntity.class);
        Join<ServiceEntity, ServiceDescriptionEntity> joinServiceDescription = root.join("serviceDescription");
        Join<ServiceDescriptionEntity, ClientEntity> joinClient = joinServiceDescription.join("client");
        tq.multiselect(root.get("serviceCode"), root.get("serviceVersion"));
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.equal(joinClient.get("identifier").<String>get("xRoadInstance"),
                serviceProviderId.getXRoadInstance()));
        predicates.add(builder.equal(joinClient.get("identifier").<String>get("memberClass"),
                serviceProviderId.getMemberClass()));
        predicates.add(builder.equal(joinClient.get("identifier").<String>get("memberCode"),
                serviceProviderId.getMemberCode()));
        if (serviceProviderId.getSubsystemCode() == null) {
            predicates.add(builder.isNull(joinClient.get("identifier").<String>get("subsystemCode")));
        } else {
            predicates.add(builder.equal(joinClient.get("identifier").<String>get("subsystemCode"),
                    serviceProviderId.getSubsystemCode()));
        }
        if (descriptionType != null && descriptionType.length > 0) {
            predicates.add(joinServiceDescription.get("type").in((Object[]) descriptionType));
        }
        tq.where(predicates.toArray(new Predicate[]{}));
        List<Tuple> resultList = session.createQuery(tq).getResultList();
        List<ServiceIdEntity> services = new ArrayList<>();
        for (Tuple tuple : resultList) {
            services.add(ServiceIdEntity.create(serviceProviderId, (String) tuple.get(0),
                    (String) tuple.get(1)));
        }
        return services;
    }

    @SuppressWarnings("squid:S1192")
    private ServiceEntity find(Session session, ServiceId id) {
        String query = """
                select s from ServiceEntity s
                 inner join fetch s.serviceDescription w
                 inner join fetch w.client c
                 where s.serviceCode = :serviceCode
                 and s.serviceVersion %s
                 and c.identifier.xRoadInstance = :clientInstance
                 and c.identifier.memberClass = :clientClass
                 and c.identifier.memberCode = :clientCode
                 and c.identifier.subsystemCode %s
                """
                .formatted(
                        nullOrName(id.getServiceVersion(), "serviceVersion"),
                        nullOrName(id.getClientId().getSubsystemCode(), CLIENT_SUBSYSTEM_CODE)
                );

        Query<ServiceEntity> q = session.createQuery(query, ServiceEntity.class);

        q.setParameter("serviceCode", id.getServiceCode());
        setString(q, "serviceVersion", id.getServiceVersion());
        q.setParameter("clientInstance", id.getClientId().getXRoadInstance());
        q.setParameter("clientClass", id.getClientId().getMemberClass());
        q.setParameter("clientCode", id.getClientId().getMemberCode());
        setString(q, CLIENT_SUBSYSTEM_CODE, id.getClientId().getSubsystemCode());

        return findOne(q);
    }
}
