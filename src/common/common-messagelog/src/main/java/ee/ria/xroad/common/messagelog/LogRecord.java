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
package ee.ria.xroad.common.messagelog;

/**
 * Declares methods that a log record should have.
 */
public interface LogRecord {

    /**
     * @return ID of the log record
     */
    Long getId();

    /**
     * Sets the ID of the log record.
     *
     * @param nr the ID of the log record
     */
    void setId(Long nr);

    /**
     * Sets the timestamp of the log record's creation.
     *
     * @param time the timestamp
     */
    void setTime(Long time);

    /**
     * @return the timestamp of the log record's creation
     */
    Long getTime();

    /**
     * @return true if the log record is archived
     */
    boolean isArchived();

    /**
     * Sets whether this log record is archived.
     *
     * @param isArchived whether this log record is archived
     */
    void setArchived(boolean isArchived);

    /**
     * @return the log record linking info fields
     */
    Object[] getLinkingInfoFields();
}
