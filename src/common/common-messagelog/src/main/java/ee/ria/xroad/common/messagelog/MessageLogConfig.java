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

package ee.ria.xroad.common.messagelog;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import lombok.Data;

import java.util.Map;

// todo: should be renamed to MessageLogProperties to be consistent with other classes. But first MessageLogProperties
//  should be refactored to use configuration properties.
@Data
@ConfigMapping(prefix = "xroad.message-log")
public class MessageLogConfig {

    @WithName("archive-path")
    private String archivePath;

    @WithName("archive-interval")
    private String archiveInterval;

    @WithName("archive-transaction-batch")
    private int archiveTransactionBatchSize;

    @WithName("archive-transfer-command")
    private String archiveTransferCommand;

    @WithName("clean-interval")
    private String cleanInterval;

    @WithName("clean-transaction-batch-size")
    private int cleanTransactionBatchSize;

    @WithName("keep-records-for")
    private int keepRecordsForDays;

    @WithName("hibernate")
    private Map<String, String> hibernate; // messagelog.hibernate.* properties from db-properties file
}
