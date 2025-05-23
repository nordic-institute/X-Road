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

import ee.ria.xroad.common.identifier.ServiceId;

import org.hibernate.MultiIdentifierLoadAccess;
import org.hibernate.Session;
import org.niis.xroad.serverconf.impl.entity.ServiceDescriptionEntity;
import org.niis.xroad.serverconf.impl.entity.ServiceEntity;

import java.util.List;

/**
 * Service description data access object implementation.
 */
public class ServiceDescriptionDAOImpl extends AbstractDAOImpl<ServiceDescriptionEntity> {

    /**
     * Returns the service description of the given service identifier.
     * @param session the session
     * @param id the service identifier
     * @return the service description of the given service identifier
     */
    public ServiceDescriptionEntity getServiceDescription(Session session, ServiceId id) {
        ServiceEntity service =
                new ServiceDAOImpl().getService(session, id);
        if (service != null) {
            return service.getServiceDescription();
        }

        return null;
    }

    /**
     * Returns the service description of the given ServiceDescriptionEntity PK.
     * @param session the session
     * @param id the ServiceDescriptionEntity PK
     * @return the service description of the given service identifier
     */
    public ServiceDescriptionEntity getServiceDescription(Session session, Long id) {
        return session.get(ServiceDescriptionEntity.class, id);
    }


    /**
     * Returns multiple service descriptions matching given ids
     * @param session the session
     * @param ids
     * @return
     */
    public List<ServiceDescriptionEntity> getServiceDescriptions(Session session, Long... ids) {
        MultiIdentifierLoadAccess<ServiceDescriptionEntity> multiLoadAccess =
                session.byMultipleIds(ServiceDescriptionEntity.class);
        return multiLoadAccess.multiLoad(ids);
    }

}
