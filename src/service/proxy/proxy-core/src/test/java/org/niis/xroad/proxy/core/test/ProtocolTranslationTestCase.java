/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
package org.niis.xroad.proxy.core.test;

/**
 * Marker interface for protocol translation test cases.
 * <p>
 * Test cases implementing this interface test the translation between
 * Protocol 4.0 (V4) and Protocol 5.0 (V5) terminology:
 * <ul>
 *   <li>V4: xRoadInstance, memberClass, memberCode, serverCode</li>
 *   <li>V5: dataspaceInstance, participantClass, participantCode, connectorCode</li>
 * </ul>
 * </p>
 * <p>
 * These tests are run in a separate TestFactory in ProxyTests to isolate
 * them from the normal message flow tests.
 * </p>
 */
public interface ProtocolTranslationTestCase {
    // Marker interface - no methods required
}
