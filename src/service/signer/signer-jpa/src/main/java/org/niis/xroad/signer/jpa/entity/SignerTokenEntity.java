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
package org.niis.xroad.signer.jpa.entity;

import jakarta.persistence.Access;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.niis.xroad.common.jpa.entity.AuditableEntity;

import java.util.Arrays;
import java.util.Objects;

import static jakarta.persistence.AccessType.FIELD;

@Getter
@Setter
@Entity
@Access(FIELD)
@Table(name = SignerTokenEntity.TABLE_NAME)
public class SignerTokenEntity extends AuditableEntity {

    public static final String TABLE_NAME = "signer_tokens";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "friendly_name")
    private String friendlyName;

    @Column(name = "serial_no")
    private String serialNo;

    @Column(name = "label")
    private String label;

    @Column(name = "pin")
    private byte[] pin;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SignerTokenEntity that = (SignerTokenEntity) o;
        return Objects.equals(id, that.id)
                && Objects.equals(externalId, that.externalId)
                && Objects.equals(type, that.type)
                && Objects.equals(friendlyName, that.friendlyName)
                && Objects.equals(serialNo, that.serialNo)
                && Objects.equals(label, that.label)
                && Objects.deepEquals(pin, that.pin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, externalId, type, friendlyName, serialNo, label, Arrays.hashCode(pin));
    }
}
