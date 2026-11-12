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
package org.niis.xroad.ds.identityhub.application;

import org.eclipse.edc.spi.system.ServiceExtension;
import org.junit.jupiter.api.Test;
import org.niis.xroad.edc.extension.bridge.ExtensionScanGuard;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ExtensionScanTest {

    private static final String STOCK_STS_CONFIGURATION_EXTENSION =
            "org.eclipse.edc.api.iam.decentralizedclaims.sts.StsApiConfigurationExtension";
    private static final String XROAD_STS_CONFIGURATION_EXTENSION =
            "org.niis.xroad.ds.identityhub.sts.XRoadStsApiConfigurationExtension";

    @Test
    void everyNonExcludedExtensionOnTheRuntimeClasspathIsScannable() {
        assertThatCode(this::scan).doesNotThrowAnyException();
    }

    @Test
    void stsConfigurationIsProvidedByTheXRoadExtensionOnly() {
        var scanned = scan().stream().map(extension -> extension.getClass().getName()).toList();

        assertThat(scanned)
                .contains(XROAD_STS_CONFIGURATION_EXTENSION)
                .doesNotContain(STOCK_STS_CONFIGURATION_EXTENSION);
    }

    @Test
    void sqlSchemaBootstrapperIsProvidedByTheNoOpExtensionOnly() {
        var scanned = scan().stream().map(extension -> extension.getClass().getName()).toList();

        assertThat(scanned)
                .contains("org.niis.xroad.edc.extension.bridge.XRoadNoOpSqlSchemaBootstrapperExtension")
                .doesNotContain("org.eclipse.edc.sql.bootstrapper.SqlSchemaBootstrapperExtension");
    }

    private List<ServiceExtension> scan() {
        return ExtensionScanGuard.scanNonExcluded(ExtensionScanTest.class.getResource("/application.yaml"));
    }
}
