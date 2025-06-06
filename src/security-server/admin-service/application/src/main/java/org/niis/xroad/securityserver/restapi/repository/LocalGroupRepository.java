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

import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.niis.xroad.restapi.util.PersistenceUtils;
import org.niis.xroad.serverconf.impl.dao.GroupMemberDAOImpl;
import org.niis.xroad.serverconf.impl.dao.LocalGroupDAOImpl;
import org.niis.xroad.serverconf.impl.entity.ClientIdEntity;
import org.niis.xroad.serverconf.impl.entity.GroupMemberEntity;
import org.niis.xroad.serverconf.impl.entity.LocalGroupEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * localGroupEntity repository
 */
@Repository
@Transactional
@RequiredArgsConstructor
public class LocalGroupRepository {

    private final PersistenceUtils persistenceUtils;
    private final GroupMemberDAOImpl groupMemberDAO = new GroupMemberDAOImpl();

    public LocalGroupEntity getLocalGroup(Long entityId) {
        LocalGroupDAOImpl localGroupDAO = new LocalGroupDAOImpl();
        return localGroupDAO.getLocalGroup(persistenceUtils.getCurrentSession(), entityId);
    }

    public void deleteGroupMembersByMemberId(ClientIdEntity memberId) {
        groupMemberDAO.deleteByGroupMemberId(persistenceUtils.getCurrentSession(), memberId);
    }

    /**
     * Executes a Hibernate persist(localGroupEntity)
     * @param localGroupEntity localGroupEntity
     */
    public void persist(LocalGroupEntity localGroupEntity) {
        persistenceUtils.getCurrentSession().persist(localGroupEntity);
    }

    /**
     * Executes a Hibernate persist(GroupMemberEntity) for multiple group members
     * @param groupMemberEntities list of GroupMemberEntity
     */
    public void saveOrUpdateAll(List<GroupMemberEntity> groupMemberEntities) {
        Session session = persistenceUtils.getCurrentSession();
        for (GroupMemberEntity groupMemberEntity : groupMemberEntities) {
            session.persist(groupMemberEntity);
        }
    }

    /**
     * Executes a Hibernate delete(localGroupEntity)
     * @param localGroupEntity LocalGroupEntity
     */
    public void delete(LocalGroupEntity localGroupEntity) {
        persistenceUtils.getCurrentSession().remove(localGroupEntity);
    }
}
