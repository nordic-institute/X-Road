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
 package ee.ria.xroad.common.certificateprofile;

/**
 * DistinguishedName field description for user interfaces.
 */
public interface DnFieldDescription {

    /**
     * Returns the identifier of the field (such as 'O', 'OU' etc).
     * @return the internal identifier of the field
     */
    String getId();

    /**
     * Returns the label of the field, used to display the field in
     * the user interface.
     * @return the label of the field
     */
    String getLabel();

    /**
     * Returns the default value of the field. Can be empty or null.
     * @return the value of the field
     */
    String getDefaultValue();

    /**
     * Must return true if the field is to be made read-only in the
     * user interface.
     * @return true, if this field is read-only
     */
    boolean isReadOnly();

    /**
     * Hint for user interface to indicate that this field is required to
     * be filled.
     * @return true, if this field is required to be filled
     */
    boolean isRequired();
}
