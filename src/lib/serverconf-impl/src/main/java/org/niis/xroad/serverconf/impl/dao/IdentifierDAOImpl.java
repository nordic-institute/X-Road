/*
 * The MIT License
 *
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
import org.niis.xroad.serverconf.entity.ClientIdEntity;
import org.niis.xroad.serverconf.entity.GlobalGroupIdEntity;
import org.niis.xroad.serverconf.entity.LocalGroupIdEntity;
import org.niis.xroad.serverconf.entity.SecurityServerIdEntity;
import org.niis.xroad.serverconf.entity.ServiceIdEntity;
import org.niis.xroad.serverconf.entity.XRoadIdEntity;

import java.util.List;

/**
 * Identifier data access object implementation.
 */
public class IdentifierDAOImpl extends AbstractDAOImpl<XRoadIdEntity> {

    private static final String X_ROAD_INSTANCE = "xRoadInstance";
    private static final String MEMBER_CLASS = "memberClass";
    private static final String MEMBER_CODE = "memberCode";
    private static final String SUBSYSTEM_CODE = "subsystemCode";
    private static final String SERVICE_VERSION = "serviceVersion";
    private static final String GROUP_CODE = "groupCode";

    /**
     * Finds a (local) client identifier corresponding the example or null if none exits
     */
    public ClientIdEntity findClientId(Session session, ClientId example) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<ClientIdEntity> query = cb.createQuery(ClientIdEntity.class);
        final Root<ClientIdEntity> from = query.from(ClientIdEntity.class);

        Predicate pred = cb.and(
                cb.equal(from.get(X_ROAD_INSTANCE), example.getXRoadInstance()),
                cb.equal(from.get(MEMBER_CLASS), example.getMemberClass()),
                cb.equal(from.get(MEMBER_CODE), example.getMemberCode()));
        if (example.getSubsystemCode() == null) {
            pred = cb.and(pred, cb.isNull(from.get(SUBSYSTEM_CODE)));
        } else {
            pred = cb.and(pred, cb.equal(from.get(SUBSYSTEM_CODE), example.getSubsystemCode()));
        }

        final List<ClientIdEntity> list = session.createQuery(query.select(from).where(pred))
                .setMaxResults(1)
                .setCacheable(true)
                .getResultList();

        return list.isEmpty() ? null : list.getFirst();
    }

    public ServiceIdEntity findServiceId(Session session, ServiceId example) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<ServiceIdEntity> query = cb.createQuery(ServiceIdEntity.class);
        final Root<ServiceIdEntity> from = query.from(ServiceIdEntity.class);

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

        final List<ServiceIdEntity> list = session.createQuery(query.select(from).where(pred))
                .setMaxResults(1)
                .setCacheable(true)
                .getResultList();

        return list.isEmpty() ? null : list.getFirst();
    }

    public SecurityServerIdEntity findSecurityServerId(Session session, SecurityServerId example) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<SecurityServerIdEntity> query = cb.createQuery(SecurityServerIdEntity.class);
        final Root<SecurityServerIdEntity> from = query.from(SecurityServerIdEntity.class);

        Predicate pred = cb.and(
                cb.equal(from.get(X_ROAD_INSTANCE), example.getXRoadInstance()),
                cb.equal(from.get(MEMBER_CLASS), example.getMemberClass()),
                cb.equal(from.get(MEMBER_CODE), example.getMemberCode()),
                cb.equal(from.get("serverCode"), example.getServerCode()));


        final List<SecurityServerIdEntity> list = session.createQuery(query.select(from).where(pred))
                .setMaxResults(1)
                .setCacheable(true)
                .getResultList();

        return list.isEmpty() ? null : list.getFirst();
    }

    public GlobalGroupIdEntity findGlobalGroupId(Session session, GlobalGroupId example) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<GlobalGroupIdEntity> query = cb.createQuery(GlobalGroupIdEntity.class);
        final Root<GlobalGroupIdEntity> from = query.from(GlobalGroupIdEntity.class);

        Predicate pred = cb.and(
                cb.equal(from.get(X_ROAD_INSTANCE), example.getXRoadInstance()),
                cb.equal(from.get(GROUP_CODE), example.getGroupCode()));


        final List<GlobalGroupIdEntity> list = session.createQuery(query.select(from).where(pred))
                .setMaxResults(1)
                .setCacheable(true)
                .getResultList();

        return list.isEmpty() ? null : list.getFirst();
    }

    public LocalGroupIdEntity findLocalGroupId(Session session, LocalGroupId example) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<LocalGroupIdEntity> query = cb.createQuery(LocalGroupIdEntity.class);
        final Root<LocalGroupIdEntity> from = query.from(LocalGroupIdEntity.class);

        Predicate pred = cb.and(
                cb.isNull(from.get(X_ROAD_INSTANCE)),
                cb.equal(from.get(GROUP_CODE), example.getGroupCode()));


        final List<LocalGroupIdEntity> list = session.createQuery(query.select(from).where(pred))
                .setMaxResults(1)
                .setCacheable(true)
                .getResultList();

        return list.isEmpty() ? null : list.getFirst();
    }
}
