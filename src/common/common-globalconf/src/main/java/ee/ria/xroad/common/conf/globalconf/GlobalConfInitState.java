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
package ee.ria.xroad.common.conf.globalconf;

/**
 * Enum representing the state of GlobalConf initialization.
 */
public enum GlobalConfInitState {
    /**
     * Default state if state is not known.
     */
    UNKNOWN,
    /**
     * Unexpected exception has occurred during initialization.
     */
    FAILURE_UNEXPECTED,
    /**
     * GlobalConf source is misconfigured.
     */
    FAILURE_CONFIGURATION_ERROR,
    /**
     * GlobalConf anchor file is missing.
     */
    FAILURE_MISSING_ANCHOR,
    /**
     * Instance-identifier is missing.
     */
    FAILURE_MISSING_INSTANCE_IDENTIFIER,
    /**
     * GlobalConf is malformed. (Missing required files, etc.)
     */
    FAILURE_MALFORMED,
    /**
     * GlobalConf is ready to be initialized.
     */
    READY_TO_INIT,
    /**
     * GlobalConf has been initialized.
     */
    INITIALIZED
}
