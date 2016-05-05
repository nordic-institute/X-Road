/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/**
 * Holds configuration client status information
 */
@Getter
@ToString
public class DiagnosticsStatus implements Serializable {
    private static final FormatStyle FORMAT_STYLE = FormatStyle.SHORT;
    private int returnCode;
    private LocalTime prevUpdate;
    private LocalTime nextUpdate;
    @Setter
    private String description;

    /**
     * Constructor
     * @param returnCode return code
     * @param prevUpdate previous update
     */
    public DiagnosticsStatus(int returnCode, LocalTime prevUpdate) {
        this.returnCode = returnCode;
        this.prevUpdate = prevUpdate;
    }


    /**
     *
     * @param returnCode
     * @param prevUpdate
     * @param description
     */
    public DiagnosticsStatus(int returnCode, LocalTime prevUpdate, String description) {
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
    public DiagnosticsStatus(int returnCode, LocalTime prevUpdate, LocalTime nextUpdate) {
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
        this.prevUpdate = LocalTime.now();
    }

    /**
     * Get formatted previous update value
     * @return previous update
     */
    public String getFormattedPrevUpdate() {
        return getFormattedLocalTime(prevUpdate);
    }

    /**
     * Get formatted next update value
     * @return next update
     */
    public String getFormattedNextUpdate() {
        return getFormattedLocalTime(nextUpdate);
    }

    private String getFormattedLocalTime(LocalTime time) {
        if (time == null) {
            return "";
        } else {
            return time.format(DateTimeFormatter.ofLocalizedTime(FORMAT_STYLE));
        }
    }
}
