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
package org.niis.xroad.messagelog.archiver.core;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.messagelog.MessageLogEncryptionProperties;
import org.niis.xroad.messagelog.archiver.core.config.MessageLogArchiverProperties;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class MessageLogArchiverService {

    private final LogArchiver logArchiver;
    private final LogCleaner logCleaner;

    private final MessageLogArchiverProperties archiverProperties;
    private final MessageLogEncryptionProperties encryptionProperties;

    public void triggerArchival(String instanceIdentifier) {
        log.info("Received archival trigger request");
        try {
            log.info("Starting archival operation");
            logArchiver.execute(instanceIdentifier, archiverProperties, encryptionProperties);
            log.info("Archival operation completed successfully");
        } catch (Exception e) {
            log.error("Archival operation failed", e);
        }
    }

    public void triggerCleanup() {
        log.info("Received cleanup trigger request");
        try {
            log.info("Starting cleanup operation");
            logCleaner.execute(archiverProperties);
            log.info("Cleanup operation completed successfully");
        } catch (Exception e) {
            log.error("Cleanup operation failed", e);
        }
    }

}
