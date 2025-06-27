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

import lombok.Getter;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Implements password/pin complexity requirements policy.
 * <p>
 * The password must meet the following complexity requirements:
 * <ul>
 *     <li>Length >= MIN_PASSWORD_LENGTH</li>
 *     <li>Includes at least one character from MIN_CHARACTER_CLASS_COUNT character classes:</li>
 *     <ul>
 *         <li>CharacterClass.DIGIT</li>
 *         <li>CharacterClass.UPPERCASE</li>
 *         <li>CharacterClass.LOWERCASE</li>
 *         <li>CharacterClass.SPECIAL</li>
 *     </ul>
 *     <li>Does not include characters from the CharacterClass.INVALID character class</li>
 * </ul>
 */
public final class PasswordPolicy {

    public static final int MIN_PASSWORD_LENGTH = 10;
    public static final int MIN_CHARACTER_CLASS_COUNT = 3;

    /**
     * Enumeration for character classes
     */
    public enum CharacterClass {

        DIGIT,
        UPPERCASE,
        LOWERCASE,
        SPECIAL,
        INVALID;

        public static final int MIN_ACCEPTED = 32;
        public static final int MAX_ACCEPTED = 126;

        /**
         * Tells the character class of character
         * @param ch character
         * @return character class
         */
        public static CharacterClass of(char ch) {
            if (ch < MIN_ACCEPTED || ch > MAX_ACCEPTED) return INVALID;
            if (ch >= '0' && ch <= '9') return DIGIT;
            if (ch >= 'A' && ch <= 'Z') return UPPERCASE;
            if (ch >= 'a' && ch <= 'z') return LOWERCASE;
            return SPECIAL;
        }
    }

    /**
     * Checks that the specified password meets the complexity requirements.
     *
     * Note. This method cannot handle Unicode supplementary characters.
     *
     * @param password to check
     * @return true if the password meets the complexity requirements, false otherwise.
     */
    public static boolean validate(final char[] password) {
        return describe(password).isValid();
    }

    /**
     * Describes the specified password according to the policy
     *
     * Note. This method cannot handle Unicode supplementary characters.
     *
     * @param password to check
     * @return Description
     * @see Description
     */
    public static Description describe(final char[] password) {
        if (password == null || password.length == 0) {
            return new Description(0, EnumSet.noneOf(CharacterClass.class));
        }

        EnumSet<CharacterClass> classes = EnumSet.noneOf(CharacterClass.class);

        for (char ch : password) {
            classes.add(CharacterClass.of(ch));
        }

        return new Description(password.length, classes);
    }

    private PasswordPolicy() {
        //Utility class
    }

    /**
     * Password/pin description
     */
    @Getter
    public static final class Description {
        private final int length;
        private final int minLength = MIN_PASSWORD_LENGTH;
        private final Set<CharacterClass> characterClasses;
        private final int minCharacterClassCount = MIN_CHARACTER_CLASS_COUNT;

        private Description(int length, Set<CharacterClass> classes) {
            this.length = length;
            this.characterClasses = Collections.unmodifiableSet(classes);
        }

        /**
         * Tells if password/pin is valid
         * @return true if valid
         */
        public boolean isValid() {
            return this.length >= this.minLength
                    && !this.characterClasses.contains(CharacterClass.INVALID)
                    && this.characterClasses.size() >= this.minCharacterClassCount;
        }

        /**
         * Tells whether password/pin has invalid characters
         * @return true if there are invalid characters
         */
        public boolean hasInvalidCharacters() {
            return this.characterClasses.contains(CharacterClass.INVALID);
        }

    }
}
