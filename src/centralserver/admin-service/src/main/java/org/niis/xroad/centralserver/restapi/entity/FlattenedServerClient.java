/**
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
package org.niis.xroad.centralserver.restapi.entity;

import org.hibernate.annotations.Subselect;
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
 * Used only for queries though view flattened_security_server_client and {@link FlattenedSecurityServerClient}.
 * Same table is also mapped to {@link ServerClient} entity, which used for updates and any other access of this table.
 */
@Entity
@Immutable
@Table(name = FlattenedServerClient.TABLE_NAME)
// Subselect prevents table creation: https://stackoverflow.com/a/33689357
@Subselect("select * from server_clients")
public class FlattenedServerClient {
    static final String TABLE_NAME = "server_clients";

    private int id;

    private FlattenedSecurityServerClient flattenedSecurityServerClient;
    private SecurityServer securityServer;

    public FlattenedServerClient() {
        //JPA
    }

    @Id
    @Column(name = "id", unique = true, nullable = false)
    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "security_server_client_id", nullable = false)
    public FlattenedSecurityServerClient getFlattenedSecurityServerClient() {
        return flattenedSecurityServerClient;
    }

    public void setFlattenedSecurityServerClient(FlattenedSecurityServerClient flattenedSecurityServerClient) {
        this.flattenedSecurityServerClient = flattenedSecurityServerClient;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "security_server_id", nullable = false)
    public SecurityServer getSecurityServer() {
        return this.securityServer;
    }

    public void setSecurityServer(SecurityServer securityServer) {
        this.securityServer = securityServer;
    }

}


