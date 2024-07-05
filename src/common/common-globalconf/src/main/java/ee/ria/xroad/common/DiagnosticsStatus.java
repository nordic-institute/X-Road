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
package ee.ria.xroad.common;

import ee.ria.xroad.common.util.TimeUtils;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Holds configuration client status information
 */
@Getter
@ToString
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
@AllArgsConstructor
public class DiagnosticsStatus implements Serializable {
    private int returnCode;
    private OffsetDateTime prevUpdate;
    private OffsetDateTime nextUpdate;
    @Setter
    private String description;

    /**
     * Constructor
     * @param returnCode return code
     * @param prevUpdate previous update
     */
    public DiagnosticsStatus(int returnCode, OffsetDateTime prevUpdate) {
        this.returnCode = returnCode;
        this.prevUpdate = prevUpdate;
    }


    /**
     *
     * @param returnCode return code
     * @param prevUpdate previous update
     * @param description status description
     */
    public DiagnosticsStatus(int returnCode, OffsetDateTime prevUpdate, String description) {
        this.returnCode = returnCode;
        this.prevUpdate = prevUpdate;
        this.description = description;
    }

    /**
     * Constructor
     * @param returnCode return code
     * @param prevUpdate previous update
     * @param nextUpdate next update
     */
    public DiagnosticsStatus(int returnCode, OffsetDateTime prevUpdate, OffsetDateTime nextUpdate) {
        this.returnCode = returnCode;
        this.prevUpdate = prevUpdate;
        this.nextUpdate = nextUpdate;
    }

    /**
     * Set return code
     * @param newReturnCode return code
     */
    public void setReturnCodeNow(int newReturnCode) {
        this.returnCode = newReturnCode;
        this.prevUpdate = TimeUtils.offsetDateTimeNow();
    }
}
