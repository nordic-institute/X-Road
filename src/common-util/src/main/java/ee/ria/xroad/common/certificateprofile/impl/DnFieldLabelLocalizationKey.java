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
package ee.ria.xroad.common.certificateprofile.impl;

/**
 * Known DnFieldDescription labelKeys
 */
public enum DnFieldLabelLocalizationKey {
    COMMON_NAME("Common Name (CN)"),
    COUNTRY_CODE("Country Code (C)"),
    INSTANCE_IDENTIFIER("Instance Identifier (C)"),
    INSTANCE_IDENTIFIER_O("Instance Identifier (O)"),
    MEMBER_CLASS("Member Class (O)"),
    MEMBER_CLASS_OU("Member Class (OU)"),
    MEMBER_CLASS_BC("Member Class (BC)"),
    MEMBER_CODE("Member Code (CN)"),
    MEMBER_CODE_SN("Member Code (SN)"),
    ORGANIZATION_NAME("Organization Name (O)"),
    ORGANIZATION_NAME_CN("Organization Name (CN)"),
    SERIAL_NUMBER("Serial Number"),
    SERIAL_NUMBER_SN("Serial Number (SN)"),
    SERVER_CODE("Server Code (CN)"),
    SERVER_DNS_NAME("Server DNS name (CN)");

    private final String compatibilityLabel;

    DnFieldLabelLocalizationKey(String compatibilityLabel) {
        this.compatibilityLabel = compatibilityLabel;

    }
    /**
     * For backwards compatibility while we still support old UI.
     * Remove when old UI support can be removed
     * @return
     */
    @Deprecated
    public String getLabel() {
        return compatibilityLabel;
    }
}
