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
package org.niis.xroad.confproxy.util;

import ee.ria.xroad.common.CodedException;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_GLOBALCONF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.confproxy.util.OutputBuilder.resolveWithinTargetDir;

public class OutputBuilderPathTraversalTest {

    private static final Path OUTPUT_DIR = Paths.get("/var/lib/xroad/confproxy/PROXY1/1700000000000");

    @Test
    public void legitimateRelativeContentLocationIsContained() {
        Path resolved = resolveWithinTargetDir(OUTPUT_DIR, "EE", "shared-params.xml");

        assertThat(resolved).isEqualTo(OUTPUT_DIR.resolve("EE").resolve("shared-params.xml"));
    }

    @Test
    public void blankInstanceWithAbsoluteContentLocationIsRejected() {
        assertThatThrownBy(() -> resolveWithinTargetDir(OUTPUT_DIR, "", "/etc/cron.d/xrd-pwn"))
                .isInstanceOf(CodedException.class)
                .satisfies(e -> assertThat(((CodedException) e).getFaultCode()).isEqualTo(X_MALFORMED_GLOBALCONF));
    }

    @Test
    public void dotDotTraversalInContentLocationIsRejected() {
        assertThatThrownBy(() -> resolveWithinTargetDir(OUTPUT_DIR, "EE", "/../../../../etc/cron.d/xrd-pwn"))
                .isInstanceOf(CodedException.class)
                .satisfies(e -> assertThat(((CodedException) e).getFaultCode()).isEqualTo(X_MALFORMED_GLOBALCONF));
    }

    @Test
    public void pureRelativeDotDotTraversalInContentLocationIsRejected() {
        assertThatThrownBy(() -> resolveWithinTargetDir(OUTPUT_DIR, "EE", "../../../../etc/cron.d/xrd-pwn"))
                .isInstanceOf(CodedException.class)
                .satisfies(e -> assertThat(((CodedException) e).getFaultCode()).isEqualTo(X_MALFORMED_GLOBALCONF));
    }

    @Test
    public void dotDotTraversalInInstanceIdentifierIsRejected() {
        assertThatThrownBy(() -> resolveWithinTargetDir(OUTPUT_DIR, "../../../etc", "shared-params.xml"))
                .isInstanceOf(CodedException.class)
                .satisfies(e -> assertThat(((CodedException) e).getFaultCode()).isEqualTo(X_MALFORMED_GLOBALCONF));
    }

    @Test
    public void absoluteInstanceIdentifierIsRejected() {
        assertThatThrownBy(() -> resolveWithinTargetDir(OUTPUT_DIR, "/etc/cron.d", "xrd-pwn"))
                .isInstanceOf(CodedException.class)
                .satisfies(e -> assertThat(((CodedException) e).getFaultCode()).isEqualTo(X_MALFORMED_GLOBALCONF));
    }
}
