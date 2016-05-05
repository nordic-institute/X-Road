/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.identifier;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Security category ID.
 */
@XmlJavaTypeAdapter(IdentifierTypeConverter.SecurityCategoryIdAdapter.class)
public final class SecurityCategoryId extends XroadId {

    private final String securityCategory;

    SecurityCategoryId() { // required by Hibernate
        this(null, null);
    }

    private SecurityCategoryId(String xRoadInstance, String securityCategory) {
        super(XroadObjectType.SECURITYCATEGORY, xRoadInstance);

        this.securityCategory = securityCategory;
    }

    /**
     * Returns the security category code.
     * @return String
     */
    public String getCategoryCode() {
        return securityCategory;
    }

    @Override
    protected String[] getFieldsForStringFormat() {
        return new String[] {securityCategory};
    }

    /**
     * Factory method for creating a new GlobalGroupId.
     * @param xRoadInstance instance of the new security category
     * @param securityCategory code of the new security category
     * @return SecurityCategoryId
     */
    public static SecurityCategoryId create(String xRoadInstance,
            String securityCategory) {
        validateField("xRoadInstance", xRoadInstance);
        validateField("securityCategory", securityCategory);
        return new SecurityCategoryId(xRoadInstance, securityCategory);
    }

}
