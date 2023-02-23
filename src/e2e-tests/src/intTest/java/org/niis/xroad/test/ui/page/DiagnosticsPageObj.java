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
package org.niis.xroad.test.ui.page;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$$x;
import static com.codeborne.selenide.Selenide.$x;

public class DiagnosticsPageObj {

    public SelenideElement backupEncryptionStatus() {
        return $x("//div[@data-test='backup-encryption-status']");
    }

    public ElementsCollection backupEncryptionKeyList() {
        return $$x("//table[@data-test='backup-encryption-keys']/tbody/tr/td");
    }

    public SelenideElement messageLogEncryptionStatus() {
        return $x("//div[@data-test='message-log-archive-encryption-status']");
    }

    public SelenideElement messageLogDatabaseEncryptionStatus() {
        return $x("//div[@data-test='message-log-database-encryption-status']");
    }

    public SelenideElement memberMessageLogEncryptionKey() {
        return $x("//table[@data-test='member-encryption-status']")
                .$("td.status-wrapper:not(:has(i.warning-icon))");
    }

    public SelenideElement memberMessageLogEncryptionKeyWithWarning() {
        return $x("//table[@data-test='member-encryption-status']")
                .$("td.status-wrapper:has(i.warning-icon)");
    }

    public SelenideElement javaVersionMessage() {
        return $x("//td[@data-test='java-message']");
    }

    public SelenideElement globalConfigurationMessage() {
        return $x("//td[@data-test='global-configuration-message']");
    }

    public SelenideElement timestampingMessage() {
        return $x("//td[@data-test='timestamping-message']");
    }

    public SelenideElement ocspResponderMessage() {
        return $x("//td[@data-test='ocsp-responders-message']");
    }
}
