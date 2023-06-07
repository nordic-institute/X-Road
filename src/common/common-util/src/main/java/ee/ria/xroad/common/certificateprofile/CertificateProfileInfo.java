/**
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

/**
 * This interface describes the certificate profile information.
 */
public interface CertificateProfileInfo {

    /**
     * Returns the DN fields that will be displayed in the user interface for
     * the specific certificate type.
     * @return the DN fields
     */
    DnFieldDescription[] getSubjectFields();

    /**
     * Creates the DistiguishedName object from the specified fields for the
     * specific certificate type.
     * filled in the user interface.
     * @param values the field values
     * @return the DistiguishedName
     */
    javax.security.auth.x500.X500Principal createSubjectDn(DnFieldValue[] values);

    /**
     * Called when the user interface validates the field value. The
     * validation logic is implementation specific. Should throw an exception
     * if the field is invalid.
     * @param field the field value
     * @throws Exception if validation fails
     */
    void validateSubjectField(DnFieldValue field) throws Exception;
}
