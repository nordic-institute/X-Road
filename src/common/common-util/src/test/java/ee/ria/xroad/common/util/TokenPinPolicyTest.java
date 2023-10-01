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
package ee.ria.xroad.common.util;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link TokenPinPolicy}
 */
public class TokenPinPolicyTest {

    private static final Random RND = new Random(1);
    private static final char[] UPPERCASE_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final char[] LOWERCASE_LETTERS = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char[] DIGITS            = "1234567890".toCharArray();
    private static final char[] SPECIAL_CHARS     = " !\"#$%&'()*+,-./:;<=>?`@{|}~".toCharArray();

    /**
     * Test that policy rejects weak passwords
     */
    @Test
    public void shouldRejectWeakPassword() {
        assertFalse(TokenPinPolicy.validate(null));
        assertFalse(TokenPinPolicy.validate("".toCharArray()));

        assertFalse(TokenPinPolicy.validate(
                generatedPassword(TokenPinPolicy.MIN_PASSWORD_LENGTH, LOWERCASE_LETTERS)));
    }

    /**
     * Test that policy accepts strong passwords
     */
    @Test
    public void shouldAcceptStrongPassword() {
        for (int i = 0; i < 1000; i++) {
            final char[] pw = generatedPassword(
                    TokenPinPolicy.MIN_PASSWORD_LENGTH + i / 10,
                    UPPERCASE_LETTERS,
                    LOWERCASE_LETTERS,
                    DIGITS,
                    SPECIAL_CHARS);
            assertTrue(TokenPinPolicy.validate(pw));
        }
    }

    /**
     * Test that policy rejects too short passwords
     */
    @Test
    public void shouldRejectTooShortPassword() {
        final char[] pw = generatedPassword(
                TokenPinPolicy.MIN_PASSWORD_LENGTH - 1,
                UPPERCASE_LETTERS,
                LOWERCASE_LETTERS,
                DIGITS,
                SPECIAL_CHARS);
        assertFalse(TokenPinPolicy.validate(pw));
    }

    /**
     * Test that policy rejects invalid characters
     */
    @Test
    public void shouldRejectInvalidCharacters() {
        final char[] pw = generatedPassword(
                TokenPinPolicy.MIN_PASSWORD_LENGTH,
                UPPERCASE_LETTERS,
                LOWERCASE_LETTERS,
                DIGITS,
                "\u0000\u0019\u007f".toCharArray());
        assertFalse(TokenPinPolicy.validate(pw));
    }

    /**
     * Test that policy rejects passwords with not enough character classes
     */
    @Test
    public void shouldRejectPasswordThatDoesNotHaveEnoughCharacterClasses() {
        for (int i = 0; i < 1000; i++) {
            final char[] pw = generatedPassword(
                    TokenPinPolicy.MIN_PASSWORD_LENGTH,
                    UPPERCASE_LETTERS,
                    DIGITS);
            assertFalse(TokenPinPolicy.validate(pw));
        }
    }

    /**
     * Test that policy accepts passwords with enough character classes
     */
    @Test
    public void shouldAcceptPasswordThatHasEnoughCharacterClasses() {
        for (int i = 0; i < 1000; i++) {
            final char[] pw = generatedPassword(
                    TokenPinPolicy.MIN_PASSWORD_LENGTH,
                    UPPERCASE_LETTERS,
                    LOWERCASE_LETTERS,
                    DIGITS);
            assertTrue(TokenPinPolicy.validate(pw));
        }
    }

    /*
     * Create a pseudo-random password that contains characters from the specified sets.
     */
    private char[] generatedPassword(int len, char[]... charSets) {
        assert (len >= charSets.length);

        char[] password = new char[len];
        // ensure that the password has one character from each character set
        int charSetIndx = 0;
        while (charSetIndx < charSets.length) {
            final int indx = RND.nextInt(len);
            if (password[indx] == 0) {
                password[indx] = charSets[charSetIndx][RND.nextInt(charSets[charSetIndx].length)];
                charSetIndx++;
            }
        }

        for (int i = 0; i < len; i++) {
            char[] chs = charSets[RND.nextInt(charSets.length)];
            if (password[i] == 0)  {
                password[i] = chs[RND.nextInt(chs.length)];
            }
        }

        return password;
    }

}
