/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
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
 * Constants for global configuration
 */
public final class ConfigurationConstants {

    private ConfigurationConstants() {
    }

    /**
     * The content identifier for the private parameters.
     */
    public static final String CONTENT_ID_PRIVATE_PARAMETERS =
            "PRIVATE-PARAMETERS";

    /**
     * The default file name of private parameters.
     */
    public static final String FILE_NAME_PRIVATE_PARAMETERS =
            "private-params.xml";

    /**
     * The content identifier for the shared parameters.
     */
    public static final String CONTENT_ID_SHARED_PARAMETERS =
            "SHARED-PARAMETERS";

    /**
     * The default file name of shared parameters.
     */
    public static final String FILE_NAME_SHARED_PARAMETERS =
            "shared-params.xml";

    /**
     * The default file name suffix of configuration metadata.
     */
    public static final String FILE_NAME_SUFFIX_METADATA = ".metadata";

    /**
     * The content identifier for the monitoring configuration part.
     */
    public static final String CONTENT_ID_MONITORING = "MONITORING";

    /**
     * The content identifier for the OCSP fetch internal configuration part.
     */
    public static final String CONTENT_ID_OCSP_FETCH_INTERVAL = "FETCHINTERVAL";

    /**
     * The content identifier for the OCSP next update configuration part.
     */
    public static final String CONTENT_ID_OCSP_NEXT_UPDATE = "NEXTUPDATE";

}
