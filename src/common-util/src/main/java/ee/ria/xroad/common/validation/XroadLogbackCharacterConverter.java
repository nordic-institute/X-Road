/**
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

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;
import com.google.common.base.CharMatcher;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static ee.ria.xroad.common.validation.SpringFirewallValidationRules.FORBIDDEN_BOM;
import static ee.ria.xroad.common.validation.SpringFirewallValidationRules.FORBIDDEN_ZWSP;

/**
 * Logback converter that encodes unwanted/exploitable characters in log files
 */
public class XroadLogbackCharacterConverter extends CompositeConverter<ILoggingEvent> {

    private Set whitelist = new HashSet<Character>(Arrays.asList('\u0020'));
    private static final String DELIMITER = ",";

    /**
     * Override method to be able to read possible configuration parameters
     */
    @Override
    public void start() {
        super.start();
        String option = getFirstOption();
        if (option != null) {
            // int[] ints = Arrays.stream(option.split(DELIMITER)).mapToInt(Integer::parseInt).toArray();
            int charCode = Integer.parseInt(option);
            whitelist.add(String.format("\\u%04X", charCode).charAt(0));
        }
    }

    private String replaceLogForgingCharacters(String in) {
        return replaceLogForgingCharacters(in, whitelist);
    }

    /**
     * Utility method for encoding unwanted/exploitable characters in string
     * @param in input string
     * @param whitelist characters to skip in test
     * @return encoded string
     */
    public static String replaceLogForgingCharacters(String in, Set<Character> whitelist) {
        StringBuilder stringBuilder = new StringBuilder();
        for (char ch: in.toCharArray()) {
            if (!whitelist.contains(ch) && CharMatcher.whitespace()
                    .or(CharMatcher.breakingWhitespace())
                    .or(CharMatcher.javaIsoControl())
                    .or(CharMatcher.is(FORBIDDEN_BOM))
                    .or(CharMatcher.is(FORBIDDEN_ZWSP))
                    .matchesAnyOf(String.valueOf(ch))) {
                stringBuilder.append(String.format("\\u%04X", (int) ch));
            } else {
                stringBuilder.append(ch);
            }
        }
        return stringBuilder.toString();
    }

    @Override
    protected String transform(ILoggingEvent event, String in) {
        return replaceLogForgingCharacters(in);
    }
}
