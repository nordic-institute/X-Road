/**
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

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Immutable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * Entity that connects FlattenedSecurityServerClient and SecurityServer.
 * Used only for queries though view flattened_security_server_client and {@link FlattenedSecurityServerClientViewEntity}.
 * Same table is also mapped to {@link ServerClientEntity} entity, which used for updates and any other access of this table.
 */
@Entity
@Immutable
@NoArgsConstructor
@Table(name = FlattenedServerClientEntity.TABLE_NAME)
public class FlattenedServerClientEntity {

    public static final String TABLE_NAME = "server_clients";

    @Id
    @Column(name = "id", unique = true, nullable = false)
    @Getter
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "security_server_client_id", nullable = false)
    @Getter
    private FlattenedSecurityServerClientViewEntity flattenedSecurityServerClientView;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "security_server_id", nullable = false)
    @Getter
    private SecurityServerEntity securityServer;
}


