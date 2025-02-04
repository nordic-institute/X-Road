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
package org.niis.xroad.confclient.application;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.confclient.ConfClientCLIRunner;

@QuarkusMain
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class ConfClientMain {

    public static void main(String[] args) {
        Quarkus.run(ConfClientApplication.class, args);
    }

    @Slf4j
    @RequiredArgsConstructor
    public static class ConfClientApplication implements QuarkusApplication {
        private static final int ERROR_CODE_INTERNAL = 125;

        private final ConfClientCLIRunner confClientCLIRunner;

        @Override
        public int run(String... args) {
            if (args.length > 0) {
                return runCli(args);
            } else {
                Quarkus.waitForExit();
                return 0;
            }
        }

        public int runCli(String... args) {
            try {
                return confClientCLIRunner.run(args);
            } catch (Exception e) {
                log.error("Failed to run Configuration Client CLI command", e);
                return ERROR_CODE_INTERNAL;
            }
        }
    }
}
