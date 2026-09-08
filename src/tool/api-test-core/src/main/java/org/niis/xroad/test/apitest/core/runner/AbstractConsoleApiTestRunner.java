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
package org.niis.xroad.test.apitest.core.runner;

import org.junit.platform.console.ConsoleLauncher;
import org.niis.xroad.test.apitest.core.config.ApiTestConfigSource;
import org.niis.xroad.test.apitest.core.container.ClasspathResourceExtractor;

/**
 * Fat-jar entry point base for product API test suites. Activates the SmallRye {@code cli} profile,
 * extracts the compose files listed in {@link #resourceFiles} from the jar into the resource dir,
 * then launches the JUnit Platform Console Launcher against the suite class named by
 * {@link #phasedSuiteClassName}.
 */
public abstract class AbstractConsoleApiTestRunner {

    /**
     * Classpath resources to extract before launching (compose files, env files, host-mounted dirs).
     */
    protected abstract String[] resourceFiles();

    /**
     * Fully-qualified class name of the phased suite to run.
     */
    protected abstract String phasedSuiteClassName();

    /**
     * Runs the test suite. Call from the product subclass {@code main}.
     */
    protected final void run() {
        System.setProperty("smallrye.config.profile", "cli");

        var props = ApiTestConfigSource.getInstance().getCoreProperties();

        System.setProperty("allure.results.directory", props.allure().resultsDirectory());

        ClasspathResourceExtractor.extract(props.resourceDir(), resourceFiles());

        ConsoleLauncher.main(
                "execute",
                "--classpath", ".",
                "--select-class", phasedSuiteClassName(),
                "--reports-dir", props.workingDir() + "test-results",
                "--fail-if-no-tests"
        );
    }
}
