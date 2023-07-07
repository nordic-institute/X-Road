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
package ee.ria.xroad.common.validation;


import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StrictIdentifierValidatorTest {
    private StrictIdentifierValidator validator = new StrictIdentifierValidator();

    @Test
    public void valid() {
        validator = new StrictIdentifierValidator();
        assertValid(null);
        assertValid("abcdefghijklmnopqrstuvwxyz");
        assertValid("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        assertValid("1234567890");
        assertValid("'()+,-.=?");
    }

    @Test
    public void inValid() {
        assertInvalid(":");
        assertInvalid("/");
        assertInvalid("ä");
        assertInvalid("列");
    }

    private void assertValid(String string) {
        assertThat(validator.isValid(string)).isTrue();
    }

    private void assertInvalid(String s) {
        assertThat(validator.isValid(s)).isFalse();
    }


}
