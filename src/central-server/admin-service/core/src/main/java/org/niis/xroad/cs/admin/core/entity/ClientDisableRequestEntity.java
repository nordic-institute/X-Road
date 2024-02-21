/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.core.entity;

import ee.ria.xroad.common.identifier.SecurityServerId;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.admin.api.domain.Origin;

import static org.niis.xroad.cs.admin.core.entity.ClientDisableRequestEntity.DISCRIMINATOR_VALUE;

@Entity
@NoArgsConstructor
@DiscriminatorValue(DISCRIMINATOR_VALUE)
public class ClientDisableRequestEntity extends RequestEntity {
    public static final String DISCRIMINATOR_VALUE = "ClientDisableRequest";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sec_serv_user_id")
    @Getter
    @Setter
    private ClientIdEntity clientId;

    @Override
    @Transient
    public ManagementRequestType getManagementRequestType() {
        return ManagementRequestType.CLIENT_DISABLE_REQUEST;
    }

    public ClientDisableRequestEntity(Origin origin,
                                      SecurityServerId serverId,
                                      ee.ria.xroad.common.identifier.ClientId clientId,
                                      String comments) {
        super(origin, serverId, comments);
        this.clientId = ClientIdEntity.ensure(clientId);
    }
}
