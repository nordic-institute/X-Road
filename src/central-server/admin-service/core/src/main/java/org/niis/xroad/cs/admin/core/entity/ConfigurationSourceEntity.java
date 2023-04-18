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
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@NoArgsConstructor
@Table(name = ConfigurationSourceEntity.TABLE_NAME,
        uniqueConstraints = @UniqueConstraint(columnNames = {"source_type", "ha_node_name"}))
public class ConfigurationSourceEntity {

    public static final String TABLE_NAME = "configuration_sources";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = TABLE_NAME + "_id_seq")
    @SequenceGenerator(name = TABLE_NAME + "_id_seq", sequenceName = TABLE_NAME + "_id_seq", allocationSize = 1)
    @Column(name = "id", unique = true, nullable = false)
    @Getter
    private int id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "active_key_id")
    @Getter
    @Setter
    private ConfigurationSigningKeyEntity configurationSigningKey;

    @Column(name = "source_type")
    @Getter
    @Setter
    private String sourceType;

    @Column(name = "anchor_file")
    @Getter
    @Setter
    private byte[] anchorFile;

    @Column(name = "anchor_file_hash")
    @Getter
    @Setter
    private String anchorFileHash;

    @Column(name = "anchor_generated_at")
    @Getter
    @Setter
    private Instant anchorGeneratedAt;

    @Column(name = "ha_node_name")
    @Getter
    @Setter
    private String haNodeName;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "configurationSource")
    @Getter
    @Setter
    private Set<ConfigurationSigningKeyEntity> configurationSigningKeys = new HashSet<>(0);

    public ConfigurationSourceEntity(String anchorFileHash, Instant anchorGeneratedAt) {
        this.anchorFileHash = anchorFileHash;
        this.anchorGeneratedAt = anchorGeneratedAt;
    }

    public ConfigurationSourceEntity(byte[] anchorFile, String anchorFileHash, Instant anchorGeneratedAt) {
        this.anchorFileHash = anchorFileHash;
        this.anchorGeneratedAt = anchorGeneratedAt;
        this.anchorFile = anchorFile;
    }
}


