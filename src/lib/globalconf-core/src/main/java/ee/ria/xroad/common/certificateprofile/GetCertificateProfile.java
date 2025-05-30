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
package ee.ria.xroad.common.certificateprofile;

import ee.ria.xroad.common.CodedException;

import lombok.RequiredArgsConstructor;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

/**
 * Utility class for getting the certificate profile instance.
 */
@RequiredArgsConstructor
public class GetCertificateProfile {

    private final String className;

    /**
     * Returns the instance of the certificate profile provider class name.
     * Checks that the class is in classpath and that the class implements
     * the {@link ee.ria.xroad.common.certificateprofile.CertificateProfileInfoProvider} interface.
     * @return the instance of the certificate profile
     * @throws Exception if an error occurs while instantiating
     */
    public CertificateProfileInfoProvider instance() throws Exception {
        try {
            return klass().getDeclaredConstructor().newInstance();
        } catch (InstantiationException e) {
            throw new CodedException(X_INTERNAL_ERROR, e,
                    "Could not instantiate %s: %s", className, e.getMessage());
        } catch (IllegalAccessException e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }
    }

    /**
     * Returns the class that implements the
     * {@link ee.ria.xroad.common.certificateprofile.CertificateProfileInfoProvider} interface.
     * @return the class
     * if the class does not implement the interface
     */
    @SuppressWarnings("unchecked")
    public Class<CertificateProfileInfoProvider> klass() {
        try {
            Class<?> clazz = Class.forName(className);
            if (CertificateProfileInfoProvider.class.isAssignableFrom(clazz) && !clazz.isInterface()) {
                return (Class<CertificateProfileInfoProvider>) clazz;
            } else {
                throw new CodedException(X_INTERNAL_ERROR,
                        "%s must implement %s", className,
                        CertificateProfileInfoProvider.class);
            }
        } catch (ClassNotFoundException e) {
            throw new CodedException(X_INTERNAL_ERROR, e,
                    "%s could not be found in classpath", className);
        }
    }
}
