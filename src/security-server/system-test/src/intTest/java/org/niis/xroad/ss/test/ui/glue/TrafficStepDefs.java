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
