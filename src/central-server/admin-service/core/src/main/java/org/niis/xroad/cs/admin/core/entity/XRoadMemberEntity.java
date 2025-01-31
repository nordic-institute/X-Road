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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.niis.xroad.cs.admin.core.entity.validation.EntityIdentifier;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static jakarta.persistence.FetchType.EAGER;
import static jakarta.persistence.FetchType.LAZY;
import static org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity.DISCRIMINATOR_VALUE;

/**
 * Entity representing X-Road Member
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@DiscriminatorValue(DISCRIMINATOR_VALUE)
public class XRoadMemberEntity extends SecurityServerClientEntity {

    public static final String DISCRIMINATOR_VALUE = "XRoadMember";

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "member_class_id")
    @Access(AccessType.FIELD)
    @Getter
    private MemberClassEntity memberClass;

    @EntityIdentifier
    @Column(name = "member_code")
    @Getter
    @Access(AccessType.FIELD)
    private String memberCode;

    @Column(name = "administrative_contact")
    @Getter
    @Setter
    private String administrativeContact;

    @OneToMany(fetch = LAZY, mappedBy = "owner")
    @Access(AccessType.FIELD)
    @Getter
    private Set<SecurityServerEntity> ownedServers = new HashSet<>(0);

    @OneToMany(fetch = EAGER, mappedBy = "xroadMember", orphanRemoval = true)
    @Access(AccessType.FIELD)
    @Getter
    private Set<SubsystemEntity> subsystems = new HashSet<>(0);

    public XRoadMemberEntity(String name, ClientId identifier, MemberClassEntity memberClass) {
        super(MemberIdEntity.ensure(identifier), name);
        boolean isMemberClassInconsistent = !Objects.equals(identifier.getMemberClass(), memberClass.getCode());
        if (isMemberClassInconsistent) {
            throw new IllegalArgumentException("identifier and memberClass are not consistent");
        }
        this.memberCode = identifier.getMemberCode();
        this.memberClass = memberClass;
    }

}
