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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

@Entity
@Table(name = AuthCertEntity.TABLE_NAME)
@EqualsAndHashCode(exclude = {"id", "securityServer"}, callSuper = false)
@Getter
@NoArgsConstructor
public class AuthCertEntity extends AuditableEntity {

    public static final String TABLE_NAME = "auth_certs";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = TABLE_NAME + "_id_seq")
    @SequenceGenerator(name = TABLE_NAME + "_id_seq", sequenceName = TABLE_NAME + "_id_seq", allocationSize = 1)
    @Access(AccessType.FIELD)
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    @Getter
    private int id;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "security_server_id", updatable = false)
    @Access(AccessType.FIELD)
    @NotNull
    @Getter
    @Setter
    private SecurityServerEntity securityServer;

    @Column(name = "cert", updatable = false, length = 10000)
    @Access(AccessType.FIELD)
    @NotNull
    @Getter
    @Setter
    private byte[] cert;

    public AuthCertEntity(SecurityServerEntity server, byte[] certificate) {
        this.securityServer = server;
        this.cert = certificate;
    }

}
