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

import ee.ria.xroad.common.identifier.ClientId;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static org.niis.xroad.cs.admin.core.entity.SubsystemEntity.DISCRIMINATOR_VALUE;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@DiscriminatorValue(DISCRIMINATOR_VALUE)
public class SubsystemEntity extends SecurityServerClientEntity {

    public static final String DISCRIMINATOR_VALUE = "Subsystem";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "xroad_member_id")
    @Access(AccessType.FIELD)
    @Getter
    private XRoadMemberEntity xroadMember;

    @Column(name = "subsystem_code")
    @Access(AccessType.FIELD)
    @Getter
    private String subsystemCode;

    public SubsystemEntity(XRoadMemberEntity member, ClientId identifier) {
        super(SubsystemIdEntity.ensure(identifier));
        if (!identifier.subsystemContainsMember(member.getIdentifier())) {
            throw new IllegalArgumentException("Subsystem identifier does not match member");
        }
        this.xroadMember = member;
        this.subsystemCode = identifier.getSubsystemCode();
    }

}


