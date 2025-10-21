/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.test.ui.page;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$x;
import static org.openqa.selenium.By.xpath;

@SuppressWarnings("InnerClassMayBeStatic")
public class ManagementRequestsPageObj {

    private static final String DATA_ROW = "//td[@data-test='row-label' and contains(text(),'%s')]/../td[@data-test='row-value']";
    private static final String RELATIVE_DATA_ROW = ".//td[@data-test='row-label' and contains(text(),'%s')]/../td[@data-test='row-value']";

    public final RequestInformation requestInformation = new RequestInformation();
    public final SecurityServerInformation securityServerInformation = new SecurityServerInformation();
    public final Certificate certificate = new Certificate();
    public final Client client = new Client();

    public SelenideElement showOnlyPendingRequests() {
        return $x("//div[@data-test='show-only-pending-requests']");
    }

    public SelenideElement table() {
        return $x("//div[@data-test='management-requests-table']//table");
    }

    public SelenideElement tableRowOf(String text) {
        var xpath = "./tbody/tr/td/div[contains(text(), '%s')]";
        return table().find(xpath(xpath.formatted(text)));
    }

    public SelenideElement titleOfDetails(String title) {
        var xpath = "//header/span[contains(@class, 'title-view') and text()='%s']";
        return $x(xpath.formatted(title));
    }

    public SelenideElement titleOfSection(String title) {
        var xpath = "//div[@data-test='view-title']/div[@data-test='view-title-text' and text() = '%s']";
        return $x(xpath.formatted(title));
    }

    public SelenideElement search() {
        return $x("//div[@data-test='search-query-field']");
    }

    public SelenideElement tableCol(String name) {
        var xpath = "./thead/tr/th/div/span[text()='%s']/../..";
        return table().find(xpath(xpath.formatted(name)));
    }

    public SelenideElement btnApproveManagementRequest(String text) {
        var xpath = "../..//td/button[@data-test='approve-button']";
        return tableRowOf(text).find(xpath(xpath));
    }

    public SelenideElement btnApproveManagementRequest() {
        var xpath = "../..//td/button[@data-test='approve-button']";
        return table().find(xpath(xpath));
    }

    public SelenideElement btnDeclineManagementRequest(String url) {
        var xpath = "../..//td/button[@data-test='decline-button']";
        return tableRowOf(url).find(xpath(xpath));
    }

    public SelenideElement btnDeclineManagementRequest() {
        var xpath = "../..//td/button[@data-test='decline-button']";
        return table().find(xpath(xpath));
    }

    public SelenideElement clickableRequestId(String status, String type, String securityServerId) {
        var statusXpath = "../..//span[contains(text(),'%s')]";
        var typeXpath = "../../../..//span[text()='%s']";
        var requestIdXpath = "../../../td/div[contains(@class,'cursor-pointer')]";
        return tableRowOf(securityServerId)
                .find(xpath(statusXpath.formatted(status)))
                .find(xpath(typeXpath.formatted(type)))
                .find(xpath(requestIdXpath));
    }

    public class RequestInformation {
        public SelenideElement requestId() {
            return $x(DATA_ROW.formatted("Request ID"));
        }

        public SelenideElement received() {
            return $x(DATA_ROW.formatted("Received") + "/span");
        }

        public SelenideElement source() {
            return $x(DATA_ROW.formatted("Source"));
        }

        public SelenideElement status() {
            return $x(DATA_ROW.formatted("Status") + "//div[contains(@class, 'v-chip__content')]/span");
        }

        public SelenideElement comments() {
            return $x(DATA_ROW.formatted("Comments"));
        }
    }

    public class SecurityServerInformation {
        private SelenideElement securityServerInformation() {
            return $x("//div[@data-test='view-title-text' and text() = 'Affected Security Server Information']/../..");
        }

        public SelenideElement ownerName() {
            return securityServerInformation().find(xpath(RELATIVE_DATA_ROW.formatted("Owner Name")));
        }

        public SelenideElement ownerClass() {
            return securityServerInformation().find(xpath(RELATIVE_DATA_ROW.formatted("Owner Class")));
        }

        public SelenideElement ownerCode() {
            return securityServerInformation().find(xpath(RELATIVE_DATA_ROW.formatted("Owner Code")));
        }

        public SelenideElement serverCode() {
            return securityServerInformation().find(xpath(RELATIVE_DATA_ROW.formatted("Server Code")));
        }

        public SelenideElement address() {
            return securityServerInformation().find(xpath(RELATIVE_DATA_ROW.formatted("Address")));
        }
    }

    public class Certificate {
        public SelenideElement ca() {
            return $x(DATA_ROW.formatted("CA"));
        }

        public SelenideElement serialNumber() {
            return $x(DATA_ROW.formatted("Serial number"));
        }

        public SelenideElement subject() {
            return $x(DATA_ROW.formatted("Subject"));
        }

        public SelenideElement expires() {
            return $x(DATA_ROW.formatted("Expires") + "/span");
        }
    }

    public class Client {
        private SelenideElement clientInformation() {
            return $x("//div[@data-test='view-title-text' and text() = 'Client Submitted for Registration']/../..");
        }

        public SelenideElement ownerName() {
            return clientInformation().find(xpath(RELATIVE_DATA_ROW.formatted("Owner Name")));
        }

        public SelenideElement ownerClass() {
            return clientInformation().find(xpath(RELATIVE_DATA_ROW.formatted("Owner Class")));
        }

        public SelenideElement ownerCode() {
            return clientInformation().find(xpath(RELATIVE_DATA_ROW.formatted("Owner Code")));
        }

        public SelenideElement subsystemCode() {
            return clientInformation().find(xpath(RELATIVE_DATA_ROW.formatted("Subsystem Code")));
        }
    }
}
