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
 * A proxy data-plane flow's current lifecycle state. {@code flowId} is the opaque DSP process ID
 * (the transfer-process correlation id assigned by the control plane). Shared by every proxy node
 * in a clustered Security Server, so a flow created or transitioned through one node is immediately
 * visible to the others.
 *
 * <p>{@code state} is stored as the plain name of {@code org.eclipse.edc.connector.dataplane.spi.DataFlowStates}
 * rather than a JPA {@code @Enumerated} mirror enum, because this module has no compile-time dependency on
 * the EDC jar. {@code SharedDataFlowStateStore}, which does depend on EDC's SPI, is the one conversion point.
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
