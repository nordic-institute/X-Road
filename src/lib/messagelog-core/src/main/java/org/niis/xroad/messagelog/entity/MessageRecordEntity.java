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
package org.niis.xroad.messagelog.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Entity
@DiscriminatorValue("m")
public class MessageRecordEntity extends AbstractLogRecordEntity {
    @Column(name = "QUERYID", updatable = false)
    private String queryId;

    @Column(name = "MESSAGE", updatable = false, columnDefinition = "text")
    private String message;

    @Column(name = "SIGNATURE", columnDefinition = "text")
    private String signature;

    @Column(name = "MEMBERCLASS", updatable = false)
    private String memberClass;

    @Column(name = "MEMBERCODE", updatable = false)
    private String memberCode;

    @Column(name = "SUBSYSTEMCODE", updatable = false)
    private String subsystemCode;

    @Column(name = "HASHCHAIN", columnDefinition = "text")
    private String hashChain;

    @Column(name = "HASHCHAINRESULT", columnDefinition = "text")
    private String hashChainResult;

    @Column(name = "SIGNATUREHASH", columnDefinition = "text")
    private String signatureHash;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "TIMESTAMPRECORD")
    private TimestampRecordEntity timestampRecord;

    @Column(name = "TIMESTAMPHASHCHAIN", columnDefinition = "text")
    private String timestampHashChain;

    @Column(name = "RESPONSE", updatable = false)
    private boolean response;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "logrecord_id", updatable = false, nullable = false)
    private List<MessageAttachmentEntity> attachments;

    @Column(name = "XREQUESTID", updatable = false)
    private String xRequestId;

    @Column(name = "KEYID", updatable = false)
    private String keyId;

    @Column(name = "CIPHERMESSAGE", updatable = false, length = 1000000)
    private byte[] cipherMessage;
}
