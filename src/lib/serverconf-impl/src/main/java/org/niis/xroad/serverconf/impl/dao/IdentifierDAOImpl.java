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
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.Session;
import org.niis.xroad.serverconf.entity.ClientIdConfEntity;
import org.niis.xroad.serverconf.entity.GlobalGroupConfEntity;
import org.niis.xroad.serverconf.entity.LocalGroupConfEntity;
import org.niis.xroad.serverconf.entity.SecurityServerIdConfEntity;
import org.niis.xroad.serverconf.entity.ServiceIdConfEntity;
import org.niis.xroad.serverconf.entity.XRoadIdConfEntity;

import java.util.List;

/**
 * Identifier data access object implementation.
 */
public class IdentifierDAOImpl extends AbstractDAOImpl<XRoadIdConfEntity> {

    private static final String X_ROAD_INSTANCE = "xRoadInstance";
    private static final String MEMBER_CLASS = "memberClass";
    private static final String MEMBER_CODE = "memberCode";
    private static final String SUBSYSTEM_CODE = "subsystemCode";
    private static final String SERVICE_VERSION = "serviceVersion";
    private static final String GROUP_CODE = "groupCode";

    /**
     * Finds a (local) client identifier corresponding the example or null if none exits
     */
    public ClientIdConfEntity findClientId(Session session, ClientId example) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<ClientIdConfEntity> query = cb.createQuery(ClientIdConfEntity.class);
        final Root<ClientIdConfEntity> from = query.from(ClientIdConfEntity.class);

        Predicate pred = cb.and(
                cb.equal(from.get(X_ROAD_INSTANCE), example.getXRoadInstance()),
                cb.equal(from.get(MEMBER_CLASS), example.getMemberClass()),
                cb.equal(from.get(MEMBER_CODE), example.getMemberCode()));
        if (example.getSubsystemCode() == null) {
            pred = cb.and(pred, cb.isNull(from.get(SUBSYSTEM_CODE)));
        } else {
            pred = cb.and(pred, cb.equal(from.get(SUBSYSTEM_CODE), example.getSubsystemCode()));
        }

        final List<ClientIdConfEntity> list = session.createQuery(query.select(from).where(pred))
                .setMaxResults(1)
                .setCacheable(true)
                .getResultList();

        return list.isEmpty() ? null : list.getFirst();
    }

    public ServiceIdConfEntity findServiceId(Session session, ServiceId example) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<ServiceIdConfEntity> query = cb.createQuery(ServiceIdConfEntity.class);
        final Root<ServiceIdConfEntity> from = query.from(ServiceIdConfEntity.class);

        Predicate pred = cb.and(
                cb.equal(from.get(X_ROAD_INSTANCE), example.getXRoadInstance()),
                cb.equal(from.get(MEMBER_CLASS), example.getMemberClass()),
                cb.equal(from.get(MEMBER_CODE), example.getMemberCode()),
                cb.equal(from.get("serviceCode"), example.getServiceCode()));
        if (example.getSubsystemCode() == null) {
            pred = cb.and(pred, cb.isNull(from.get(SUBSYSTEM_CODE)));
        } else {
            pred = cb.and(pred, cb.equal(from.get(SUBSYSTEM_CODE), example.getSubsystemCode()));
        }
        if (example.getServiceVersion() == null) {
            pred = cb.and(pred, cb.isNull(from.get(SERVICE_VERSION)));
        } else {
            pred = cb.and(pred, cb.equal(from.get(SERVICE_VERSION), example.getServiceVersion()));
        }

        final List<ServiceIdConfEntity> list = session.createQuery(query.select(from).where(pred))
                .setMaxResults(1)
                .setCacheable(true)
                .getResultList();

        return list.isEmpty() ? null : list.getFirst();
    }

    public SecurityServerIdConfEntity findSecurityServerId(Session session, SecurityServerId example) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<SecurityServerIdConfEntity> query = cb.createQuery(SecurityServerIdConfEntity.class);
        final Root<SecurityServerIdConfEntity> from = query.from(SecurityServerIdConfEntity.class);

        Predicate pred = cb.and(
                cb.equal(from.get(X_ROAD_INSTANCE), example.getXRoadInstance()),
                cb.equal(from.get(MEMBER_CLASS), example.getMemberClass()),
                cb.equal(from.get(MEMBER_CODE), example.getMemberCode()),
                cb.equal(from.get("serverCode"), example.getServerCode()));


        final List<SecurityServerIdConfEntity> list = session.createQuery(query.select(from).where(pred))
                .setMaxResults(1)
                .setCacheable(true)
                .getResultList();

        return list.isEmpty() ? null : list.getFirst();
    }

    public GlobalGroupConfEntity findGlobalGroupId(Session session, GlobalGroupId example) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<GlobalGroupConfEntity> query = cb.createQuery(GlobalGroupConfEntity.class);
        final Root<GlobalGroupConfEntity> from = query.from(GlobalGroupConfEntity.class);

        Predicate pred = cb.and(
                cb.equal(from.get(X_ROAD_INSTANCE), example.getXRoadInstance()),
                cb.equal(from.get(GROUP_CODE), example.getGroupCode()));


        final List<GlobalGroupConfEntity> list = session.createQuery(query.select(from).where(pred))
                .setMaxResults(1)
                .setCacheable(true)
                .getResultList();

        return list.isEmpty() ? null : list.getFirst();
    }

    public LocalGroupConfEntity findLocalGroupId(Session session, LocalGroupId example) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<LocalGroupConfEntity> query = cb.createQuery(LocalGroupConfEntity.class);
        final Root<LocalGroupConfEntity> from = query.from(LocalGroupConfEntity.class);

        Predicate pred = cb.and(
                cb.isNull(from.get(X_ROAD_INSTANCE)),
                cb.equal(from.get(GROUP_CODE), example.getGroupCode()));


        final List<LocalGroupConfEntity> list = session.createQuery(query.select(from).where(pred))
                .setMaxResults(1)
                .setCacheable(true)
                .getResultList();

        return list.isEmpty() ? null : list.getFirst();
    }
}
