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
package org.niis.xroad.ss.test.ui.glue;

import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.ui.glue.mappers.ParameterMappers;
import org.niis.xroad.ss.test.ui.page.TrafficPageObj;

import static com.codeborne.selenide.Condition.empty;
import static com.codeborne.selenide.Condition.visible;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.selectorOptionOf;

public class TrafficStepDefs extends BaseUiStepDefs {
    private final TrafficPageObj trafficPage = new TrafficPageObj();

    @Step("Traffic chart is visible")
    public void trafficChartIsVisible() {
        trafficPage.trafficChart().shouldBe(visible);
    }

    @Step("Service select is {selenideValidation}")
    public void serviceSelectIsDisabled(ParameterMappers.SelenideValidation selenideValidation) {
        trafficPage.filter.selectService().shouldBe(selenideValidation.getSelenideCondition());
    }

    @Step("Client is not selected")
    public void clientIsNotSelected() {
        trafficPage.filter.selectClient().shouldBe(empty);
    }

    @Step("Exchange role is not selected")
    public void exchangeRoleIsNotSelected() {
        trafficPage.filter.selectExchangeRole().shouldBe(empty);
    }

    @Step("Status is not selected")
    public void statusIsNotSelected() {
        trafficPage.filter.selectStatus().shouldBe(empty);
    }

    @Step("Client {string} is selected")
    public void selectClient(String clientId) {
        trafficPage.filter.selectClient().clickAndSelect(clientId);
    }

    @Step("Service {string} is present")
    public void serviceIsPresent(String serviceCode) {
        trafficPage.filter.selectService().click();
        selectorOptionOf(serviceCode).shouldBe(visible);
    }

}
