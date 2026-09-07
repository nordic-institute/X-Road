/*
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
package org.niis.xroad.serverconf.impl.entity;

import jakarta.persistence.Access;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.niis.xroad.common.identifiers.jpa.entity.ClientIdEntity;
import org.niis.xroad.common.jpa.entity.AuditableEntity;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.ParticipantType;

import static jakarta.persistence.AccessType.FIELD;

/**
 * Binds a dataspace participant's derived ctx-id, DID and scheme version at first provisioning, per
 * XRDADR-41's derive-then-bind decision. One row per participant context: a {@code MEMBER} row
 * references the owning member's identifier, the single per-server {@code SYSTEM} row does not.
 *
 * <p>The at-most-one-SYSTEM-row constraint is enforced only by the Postgres partial unique index in
 * the Liquibase changelog; it has no portable JPA equivalent.
 */
@Getter
@Setter
@Entity
@Table(name = DsParticipantEntity.TABLE_NAME,
        check = @CheckConstraint(name = "valid_participant_type_identifier",
                constraint = "(participant_type = 'MEMBER' AND member_identifier IS NOT NULL) "
                        + "OR (participant_type = 'SYSTEM' AND member_identifier IS NULL)"))
@Access(FIELD)
public class DsParticipantEntity extends AuditableEntity {

    public static final String TABLE_NAME = "ds_participant";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", nullable = false, length = 16)
    private ParticipantType participantType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "member_identifier", unique = true)
    private ClientIdEntity memberIdentifier;

    @Column(name = "ctx_id", nullable = false, length = 4000)
    private String ctxId;

    @Column(name = "did", nullable = false, length = 4000)
    private String did;

    @Column(name = "scheme_version", nullable = false, length = 16)
    private String schemeVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private ParticipantState state;

}
