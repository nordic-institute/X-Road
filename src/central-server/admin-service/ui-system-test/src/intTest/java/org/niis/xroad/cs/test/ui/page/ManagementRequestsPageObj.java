/**
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

    public final RequestInformation requestInformation = new RequestInformation();
    public final SecurityServerInformation securityServerInformation = new SecurityServerInformation();
    public final Certificate certificate = new Certificate();
    public final Client client = new Client();

    public SelenideElement showOnlyPendingRequests() {
        return $x("//input[@data-test='show-only-pending-requests']");
    }

    public SelenideElement showOnlyPendingRequestsIsChecked(boolean checked) {
        var xpath = "//input[@data-test='show-only-pending-requests' and @aria-checked='%s']";
        return $x(String.format(xpath, checked));
    }

    public SelenideElement table() {
        return $x("//div[@data-test='management-requests-table']//table");
    }

    public SelenideElement tableRowOf(String text) {
        var xpath = "./tbody/tr/td/div[contains(text(), '%s')]";
        return table().find(xpath(String.format(xpath, text)));
    }

    public SelenideElement titleOfDetails(String title) {
        var xpath = "//h1[contains(text(), '%s')]";
        return $x(String.format(xpath, title));
    }

    public SelenideElement titleOfSection(String title) {
        var xpath = "//h2[contains(text(), '%s')]";
        return $x(String.format(xpath, title));
    }

    public SelenideElement search() {
        return $x("//div[@data-test='management-requests-search']");
    }

    public SelenideElement searchInput() {
        return $x("//input[@data-test='search-input']");
    }

    public SelenideElement tableCol(String name) {
        var xpath = "./thead/tr/th/span[text()='%s']/..";
        return table().find(xpath(String.format(xpath, name)));
    }

    public SelenideElement btnApproveManagementRequest(String text) {
        var xpath = "../..//td/div/div/button[@data-test='approve-button']";
        return tableRowOf(text).find(xpath(xpath));
    }

    public SelenideElement btnApproveManagementRequest() {
        var xpath = "../..//td/div/div/button[@data-test='approve-button']";
        return table().find(xpath(xpath));
    }

    public SelenideElement btnDeclineManagementRequest(String url) {
        var xpath = "../..//td/div/div/button[@data-test='decline-button']";
        return tableRowOf(url).find(xpath(xpath));
    }

    public SelenideElement btnDeclineManagementRequest() {
        var xpath = "../..//td/div/div/button[@data-test='decline-button']";
        return table().find(xpath(xpath));
    }

    public SelenideElement clickableRequestId(String status, String type, String securityServerId) {
        var statusXpath = "../..//div[contains(text(),'%s')]";
        var typeXpath = "../../..//span[text()='%s']";
        var requestIdXpath = "../../..//div[contains(@class,'request-id')]";
        return tableRowOf(securityServerId)
                .find(xpath(String.format(statusXpath, status)))
                .find(xpath(String.format(typeXpath, type)))
                .find(xpath(requestIdXpath));
    }

    public class RequestInformation {
        public SelenideElement requestId() {
            return $x("//td[@data-test='managementRequestDetails.requestId']");
        }
        public SelenideElement received() {
            return $x("//td[@data-test='managementRequestDetails.received']");
        }
        public SelenideElement source() {
            return $x("//td[@data-test='managementRequestDetails.source']");
        }
        public SelenideElement status() {
            return $x("//td[@data-test='managementRequestDetails.status']");
        }
        public SelenideElement comments() {
            return $x("//td[@data-test='managementRequestDetails.comments']");
        }
    }

    public class SecurityServerInformation {
        private SelenideElement securityServerInformation() {
            return $x("//section[@data-test='managementRequestDetails.securityServerInformation']");
        }
        public SelenideElement ownerName() {
            var xpath = "./div/div/table/tbody/tr/td[@data-test='managementRequestDetails.ownerName']";
            return securityServerInformation().find(xpath(xpath));
        }
        public SelenideElement ownerClass() {
            var xpath = "./div/div/table/tbody/tr/td[@data-test='managementRequestDetails.ownerClass']";
            return securityServerInformation().find(xpath(xpath));
        }
        public SelenideElement ownerCode() {
            var xpath = "./div/div/table/tbody/tr/td[@data-test='managementRequestDetails.ownerCode']";
            return securityServerInformation().find(xpath(xpath));
        }
        public SelenideElement serverCode() {
            var xpath = "./div/div/table/tbody/tr/td[@data-test='managementRequestDetails.serverCode']";
            return securityServerInformation().find(xpath(xpath));
        }
        public SelenideElement address() {
            var xpath = "./div/div/table/tbody/tr/td[@data-test='managementRequestDetails.address']";
            return securityServerInformation().find(xpath(xpath));
        }
    }

    public class Certificate {
        public SelenideElement ca() {
            return $x("//td[@data-test='managementRequestDetails.ca']");
        }
        public SelenideElement serialNumber() {
            return $x("//td[@data-test='managementRequestDetails.serialNumber']");
        }
        public SelenideElement subject() {
            return $x("//td[@data-test='managementRequestDetails.subject']");
        }
        public SelenideElement expires() {
            return $x("//td[@data-test='managementRequestDetails.expires']");
        }
    }

    public class Client {
        private SelenideElement clientInformation() {
            return $x("//section[@data-test='managementRequestDetails.clientInformation']");
        }
        public SelenideElement ownerName() {
            var xpath = "./div/div/table/tbody/tr/td[@data-test='managementRequestDetails.ownerName']";
            return clientInformation().find(xpath(xpath));
        }
        public SelenideElement ownerClass() {
            var xpath = "./div/div/table/tbody/tr/td[@data-test='managementRequestDetails.ownerClass']";
            return clientInformation().find(xpath(xpath));
        }
        public SelenideElement ownerCode() {
            var xpath = "./div/div/table/tbody/tr/td[@data-test='managementRequestDetails.ownerCode']";
            return clientInformation().find(xpath(xpath));
        }
        public SelenideElement subsystemCode() {
            var xpath = "./div/div/table/tbody/tr/td[@data-test='managementRequestDetails.subsystemCode']";
            return clientInformation().find(xpath(xpath));
        }
    }
}
