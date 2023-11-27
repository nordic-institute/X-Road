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

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$$x;
import static com.codeborne.selenide.Selenide.$x;
import static java.lang.String.format;

public class SystemParametersPageObj {
    public final DialogAddTimestampingService dialogAddTimestampingService = new DialogAddTimestampingService();
    public final DialogEditServerAddress dialogEditServerAddress = new DialogEditServerAddress();
    public final ConfigurationAnchor configurationAnchor = new ConfigurationAnchor();

    public SelenideElement btnAddTimestampingService() {
        return $x("//button[@data-test='system-parameters-timestamping-services-add-button']");
    }

    public SelenideElement tableServerAddress() {
        return $x("//tbody[@data-test='system-parameters-server-address-table-body']");
    }

    public SelenideElement btnEditServerAddress() {
        return $x("//button[@data-test='change-server-address-button']");
    }

    public SelenideElement tableTimestampingServices() {
        return $x("//tbody[@data-test='system-parameters-timestamping-services-table-body']");
    }

    public ElementsCollection tableTimestampingServicesRows() {
        return $$x("//tr[@data-test='system-parameters-timestamping-service-row']");
    }

    public SelenideElement tableTimestampingServiceNameByRow(int index, String name) {
        return tableTimestampingServicesRows().get(index).$x(format("./td[1][text() = '%s']", name));
    }

    public SelenideElement tableTimestampingServiceUrlByRow(int index, String url) {
        return tableTimestampingServicesRows().get(index).$x(format("./td[2][text() = '%s']", url));
    }

    public SelenideElement btnDeleteTimestampingServicesByRow(int index) {
        return $$x("//tr[@data-test='system-parameters-timestamping-service-row']")
                .get(index).$x(".//button[@data-test='system-parameters-timestamping-service-delete-button']");
    }

    public static class DialogEditServerAddress {
        public SelenideElement addressField() {
            return $x("//div[@data-test='security-server-address-edit-field']");
        }
    }

    public static class DialogAddTimestampingService {
        public SelenideElement radioGroupTimestampingServices() {
            return $x("//div[@data-test='system-parameters-add-timestamping-service-dialog-radio-group']");
        }

        public SelenideElement radioGroupTimestampingServicesSelection(int index) {
            return radioGroupTimestampingServices().$x(".//div[@class='v-selection-control__input']", index);
        }

        public SelenideElement btnAdd() {
            return $x("//button[@data-test='system-parameters-add-timestamping-service-dialog-add-button']");
        }

        public SelenideElement btnCancel() {
            return $x("//button[@data-test='system-parameters-add-timestamping-service-dialog-cancel-button']");
        }
    }

    public static class ConfigurationAnchor {
        public SelenideElement btnDownload() {
            return $x("//button[@data-test='system-parameters-configuration-anchor-download-button']");
        }

        public SelenideElement btnUpload() {
            return $x("//button[@data-test='system-parameters-configuration-anchor-download-button']");
        }

    }
}
