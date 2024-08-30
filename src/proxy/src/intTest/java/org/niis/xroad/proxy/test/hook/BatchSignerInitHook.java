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

package org.niis.xroad.proxy.test.hook;

import ee.ria.xroad.common.TestSecurityUtil;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconf.TestGlobalConfImpl;
import ee.ria.xroad.common.signature.BatchSigner;

import com.nortal.test.core.services.hooks.BeforeSuiteHook;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static java.lang.String.format;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchSignerInitHook implements BeforeSuiteHook {
    private static final String CONTAINER_FILES_PATH = "build/resources/intTest/signer-container-files/%s";

    @Override
    @SneakyThrows
    public void beforeSuite() {
        System.setProperty("xroad.common.configuration-path", format(CONTAINER_FILES_PATH, "etc/xroad/globalconf"));
        System.setProperty("xroad.signer.key-configuration-file", format(CONTAINER_FILES_PATH, "etc/xroad/signer/keyconf.xml"));

        TestSecurityUtil.initSecurity();
        BatchSigner.init();
        GlobalConf.initialize(new TestGlobalConfImpl());
    }

    @Override
    public int beforeSuiteOrder() {
        return DEFAULT_ORDER + 100;
    }
}
