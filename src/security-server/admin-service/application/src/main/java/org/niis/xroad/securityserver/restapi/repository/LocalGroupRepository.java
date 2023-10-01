/*
 * The MIT License
 *
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
package org.niis.xroad.securityserver.restapi.repository;

import ee.ria.xroad.common.conf.serverconf.dao.LocalGroupDAOImpl;
import ee.ria.xroad.common.conf.serverconf.model.GroupMemberType;
import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.niis.xroad.restapi.util.PersistenceUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * LocalGroup repository
 */
@Slf4j
@Repository
@Transactional
@RequiredArgsConstructor
public class LocalGroupRepository {

    private final PersistenceUtils persistenceUtils;

    public LocalGroupType getLocalGroup(Long entityId) {
        LocalGroupDAOImpl localGroupDAO = new LocalGroupDAOImpl();
        return localGroupDAO.getLocalGroup(persistenceUtils.getCurrentSession(), entityId);
    }

    /**
     * Executes a Hibernate persist(localGroupType)
     * @param localGroupType
     */
    public void persist(LocalGroupType localGroupType) {
        persistenceUtils.getCurrentSession().persist(localGroupType);
    }

    /**
     * Executes a Hibernate persist(groupMemberType) for multiple group members
     * @param groupMemberTypes
     */
    public void saveOrUpdateAll(List<GroupMemberType> groupMemberTypes) {
        Session session = persistenceUtils.getCurrentSession();
        for (GroupMemberType groupMemberType : groupMemberTypes) {
            session.saveOrUpdate(groupMemberType);
        }
    }

    /**
     * Executes a Hibernate saveOrUpdate(localGroupType)
     * @param localGroupType
     */
    public void saveOrUpdate(LocalGroupType localGroupType) {
        persistenceUtils.getCurrentSession().saveOrUpdate(localGroupType);
    }

    /**
     * Executes a Hibernate saveOrUpdate(groupMemberType)
     * @param groupMemberType
     */
    public void saveOrUpdate(GroupMemberType groupMemberType) {
        persistenceUtils.getCurrentSession().saveOrUpdate(groupMemberType);
    }

    /**
     * Executes a Hibernate delete(localGroupType)
     * @param localGroupType
     * @return
     */
    public void delete(LocalGroupType localGroupType) {
        persistenceUtils.getCurrentSession().delete(localGroupType);
    }
}
