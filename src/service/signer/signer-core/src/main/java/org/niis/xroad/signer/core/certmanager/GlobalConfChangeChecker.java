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
package org.niis.xroad.signer.core.certmanager;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Detects global conf changes
 */
@Slf4j
public class GlobalConfChangeChecker {

    private Map<String, Object> values = new HashMap<>();

    private Map<String, Boolean> changes = new HashMap<>();

    /**
     * Add value change
     * @return true if the value changed
     */
    public boolean addChange(String key, Object value) {
        Object oldValue = values.get(key);
        boolean changed = (oldValue != null && value != null && !value.equals(oldValue));
        values.put(key, value);
        changes.put(key, changed);
        log.trace("key: {}, oldValue: {} newValue: {} changed: {}", key, oldValue, value, changed);
        return changed;
    }

    /**
     * Tells whether value has changed
     * @return true if the value has changed
     */
    public boolean hasChanged(String key) {
        Boolean changed = changes.get(key);
        boolean result = changed != null && changed;
        log.trace("key: {} result: {}", key, result);
        return result;
    }
}
