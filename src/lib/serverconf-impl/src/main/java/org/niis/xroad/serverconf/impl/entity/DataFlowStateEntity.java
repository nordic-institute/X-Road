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
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.niis.xroad.common.jpa.entity.AuditableEntity;

import static jakarta.persistence.AccessType.FIELD;

/**
 * A proxy data-plane flow's current lifecycle state. {@code flowId} is the DSP process ID — the
 * transfer-process correlation identifier the control plane assigns and carries in every signaling
 * message ({@code processId} in the message body; the same value the DPS callback-URL convention
 * calls {@code transferId}), opaque and of no fixed format per the Dataplane Signaling spec. Shared
 * by every proxy node in a clustered Security Server, so a flow created or transitioned through one
 * node is immediately visible to the others.
 *
 * <p>{@code state} is stored as the plain name of {@code org.eclipse.edc.connector.dataplane.spi.DataFlowStates}
 * — deliberately not a JPA {@code @Enumerated} mapping onto a locally-declared mirror enum: this module
 * (serverconf-impl) has no compile-time dependency on the EDC jar, and duplicating that vendor enum's
 * constant names here would need to be kept in hand-sync with every future EDC upgrade or become a second,
 * silently-drifting source of truth. The one, single conversion point is {@code SharedDataFlowStateStore},
 * in the module that already depends on EDC's SPI.
 */
@Getter
@Setter
@Entity
@Table(name = DataFlowStateEntity.TABLE_NAME)
@Access(FIELD)
public class DataFlowStateEntity extends AuditableEntity {

    public static final String TABLE_NAME = "dataflow_state";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Column(name = "flow_id", unique = true, nullable = false, length = 255)
    private String flowId;

    @Column(name = "state", nullable = false, length = 32)
    private String state;

}
