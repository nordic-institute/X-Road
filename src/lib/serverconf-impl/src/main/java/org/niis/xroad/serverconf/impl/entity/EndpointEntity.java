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
import org.niis.xroad.serverconf.model.BaseEndpoint;

import static jakarta.persistence.AccessType.FIELD;

@Getter
@Setter
@Entity
@Table(name = EndpointEntity.TABLE_NAME)
@Access(FIELD)
public class EndpointEntity implements BaseEndpoint {

    public static final String TABLE_NAME = "endpoint";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Column(name = "servicecode", nullable = false)
    private String serviceCode;

    @Column(name = "method", nullable = false)
    private String method;

    @Column(name = "path", nullable = false)
    private String path;

    @Column(name = "generated", nullable = false)
    private boolean generated;

    public EndpointEntity() {
    }

    private EndpointEntity(String serviceCode, String method, String path, boolean generated) {
        this.serviceCode = serviceCode;
        this.method = method;
        this.path = path;
        this.generated = generated;

        validateArguments();
    }

    public static EndpointEntity create(String serviceCode, String method, String path, boolean generated) {
        return new EndpointEntity(serviceCode, method, path, generated);
    }
}
