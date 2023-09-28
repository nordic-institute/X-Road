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
package ee.ria.xroad.common.conf.serverconf.dao;

import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;
import ee.ria.xroad.common.identifier.ClientId;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;

/**
 * LocalGroupDAO
 */
public class LocalGroupDAOImpl {

    /**
     * Return the local group by groupcode and client id
     * @param session
     * @param groupCode
     * @param groupOwnerId
     * @return LocalGroupType
     */
    public LocalGroupType findLocalGroup(Session session, String groupCode, ClientId groupOwnerId) {
        return new ClientDAOImpl().getClient(session, groupOwnerId).getLocalGroup().stream()
                .filter(g -> StringUtils.equals(groupCode, g.getGroupCode()))
                .findFirst().orElse(null);
    }

    /**
     * Returns the LocalGroupType for the given LocalGroupType id.
     * @param session the session
     * @param id the LocalGroupType id
     * @return the LocalGroupType, or null if not found
     */
    public LocalGroupType getLocalGroup(Session session, Long id) {
        return session.get(LocalGroupType.class, id);
    }
}
