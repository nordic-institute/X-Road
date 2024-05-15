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

import ee.ria.xroad.common.CodedException;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;

import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_GLOBALCONF;


final class ContentHandler {
    ParametersProviderFactory parametersProviderFactory;

    private ContentHandler(ParametersProviderFactory parametersProviderFactory) {
        this.parametersProviderFactory = parametersProviderFactory;
    }

    static ContentHandler forVersion(String version) {
        return new ContentHandler(ParametersProviderFactory.forGlobalConfVersion(version));
    }

    PrivateParametersProvider createPrivateParametersProvider(byte[] content) throws CertificateEncodingException, IOException {
        return parametersProviderFactory.privateParametersProvider(content);
    }
    SharedParametersProvider createSharedParametersProvider(byte[] content) throws CertificateEncodingException, IOException {
        return parametersProviderFactory.sharedParametersProvider(content);
    }

    void handleContent(byte[] content, ConfigurationFile file) throws CertificateEncodingException, IOException {
        switch (file.getContentIdentifier()) {
            case ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS:
                PrivateParametersProvider pp = createPrivateParametersProvider(content);
                handlePrivateParameters(pp.getPrivateParameters(), file);
                break;
            case ConfigurationConstants.CONTENT_ID_SHARED_PARAMETERS:
                SharedParametersProvider sp = createSharedParametersProvider(content);
                handleSharedParameters(sp.getSharedParameters(), file);
                break;
            default:
                break;
        }
    }

    private void handlePrivateParameters(PrivateParameters privateParameters, ConfigurationFile file) {
        verifyInstanceIdentifier(privateParameters.getInstanceIdentifier(), file);
    }

    private void handleSharedParameters(SharedParameters sharedParameters, ConfigurationFile file) {
        verifyInstanceIdentifier(sharedParameters.getInstanceIdentifier(), file);
    }

    private void verifyInstanceIdentifier(String instanceIdentifier, ConfigurationFile file) {
        if (StringUtils.isBlank(file.getInstanceIdentifier())) {
            return;
        }

        if (!instanceIdentifier.equals(file.getInstanceIdentifier())) {
            throw new CodedException(X_MALFORMED_GLOBALCONF,
                    "Content part %s has invalid instance identifier "
                            + "(expected %s, but was %s)", file,
                    file.getInstanceIdentifier(), instanceIdentifier);
        }
    }
}
