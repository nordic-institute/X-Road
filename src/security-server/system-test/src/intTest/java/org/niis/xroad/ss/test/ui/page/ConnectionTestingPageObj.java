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
package org.niis.xroad.ss.test.ui.page;

import com.codeborne.selenide.SelenideElement;
import org.niis.xroad.common.test.ui.utils.VuetifyHelper;

import static com.codeborne.selenide.Selenide.$x;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vSelect;

public class ConnectionTestingPageObj {
    public final Filter filter = new Filter();

    public SelenideElement centralServerGlobalConfMessage(String url) {
        var xpath = "//tr[td/span[contains(text(),'%s')]]/td[@data-test='central-server-global-conf-message']";
        return $x(xpath.formatted(url));
    }

    public SelenideElement centralServerGlobalConfTestButton() {
        return $x("//button[@data-test='central-server-global-conf-test-button']");
    }

    public SelenideElement centralServerAuthCertMessage() {
        return $x("//td[@data-test='central-server-auth-cert-message']");
    }

    public SelenideElement centralServerAuthCertTestButton() {
        return $x("//button[@data-test='central-server-auth-cert-test-button']");
    }

    public SelenideElement managementServerTestButton() {
        return $x("//button[@data-test='management-server-test-button']");
    }

    public SelenideElement managementServerStatusMessage() {
        return $x("//div[@data-test='management-server-status-message']");
    }

    public SelenideElement otherSecurityServerTestButton() {
        return $x("//button[@data-test='other-security-server-test-button']");
    }

    public SelenideElement otherSecurityServerStatusMessage() {
        return $x("//div[@data-test='other-security-server-status-message']");
    }

    public SelenideElement radioRestPath() {
        return $x("//div[@data-test='other-security-server-rest-radio-button']");
    }

    public SelenideElement radioSoapPath() {
        return $x("//div[@data-test='other-security-server-soap-radio-button']");
    }

    public SelenideElement selectedTargetInstanceInput() {
        return $x("//div[@data-test='other-security-server-target-instance']//input");
    }

    public SelenideElement selectedSecurityServerIdInput() {
        return $x("//div[@data-test='other-security-server-id']//input");
    }

    public static class Filter {
        public VuetifyHelper.Select selectedClient() {
            return vSelect($x("//div[@data-test='other-security-server-client-id']"));
        }

        public VuetifyHelper.Select selectedTargetClient() {
            return vSelect($x("//div[@data-test='other-security-server-target-client-id']"));
        }
    }
}
