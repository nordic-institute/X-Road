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
import ee.ria.xroad.common.conf.serverconf.model.MessageRoomSubscriptionType;

import ee.ria.xroad.common.identifier.ClientId;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.Date;
import java.util.List;

import static ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx.doInTransaction;

/**
 * Message room subscription data access object implementation.
 */
@Slf4j
public class MessageRoomSubscriptionDAOImpl extends AbstractDAOImpl<MessageRoomSubscriptionType> {

    /**
     * Returns a list of Message Room subscriptions for the Message Room identified
     * by the ClientId ID.
     * @param id the client identifier of the Message Room publisher
     * @return the list of Message Room subscriptions for the Message Room identified
     * by the publisher identifier.
     */
    public List<MessageRoomSubscriptionType> getMessageRoomSubscriptions(Session session, ClientId id) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<MessageRoomSubscriptionType> query = cb.createQuery(MessageRoomSubscriptionType.class);
        final Root<MessageRoomSubscriptionType> from = query.from(MessageRoomSubscriptionType.class);
        final Join<MessageRoomSubscriptionType, ClientType> joinClientType = from.join("publisherClient");
        final Join<ClientType, ClientId> jointClientId = joinClientType.join("identifier");

        Predicate pred =
                cb.and(cb.equal(jointClientId.get("type"), id.getObjectType()),
                        cb.equal(jointClientId.get("xRoadInstance"), id.getXRoadInstance()),
                        cb.equal(jointClientId.get("memberClass"), id.getMemberClass()),
                        cb.equal(jointClientId.get("memberCode"), id.getMemberCode()));
        if (id.getSubsystemCode() != null) {
            pred = cb.and(pred, cb.equal(jointClientId.get("subsystemCode"), id.getSubsystemCode()));
        }


        return session.createQuery(query.select(from).where(pred))
                .setCacheable(true)
                .getResultList();
    }

    public void saveMessageRoomSubscription(Session session, MessageRoomSubscriptionType messageRoomSubscriptionType)
            throws Exception {
            messageRoomSubscriptionType.setId(getNextRecordId(session));
            messageRoomSubscriptionType.setCreated(new Date());
            session.save(messageRoomSubscriptionType);
    }

    public void deleteMessageRoomSubscription(Session session, MessageRoomSubscriptionType messageRoomSubscriptionType)
            throws Exception {
            session.delete(messageRoomSubscriptionType);
    }

    static long getNextRecordId(Session session) {
        return ((Number) session.createSQLQuery("SELECT nextval('hibernate_sequence')").getSingleResult()).longValue();
    }
}
