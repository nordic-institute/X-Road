package org.niis.xroad.ss.test.ui.glue;

import com.codeborne.selenide.Condition;
import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.ui.page.TrafficPageObj;

import static com.codeborne.selenide.Condition.visible;

public class TrafficStepDefs extends BaseUiStepDefs {
    private final TrafficPageObj trafficPage = new TrafficPageObj();

    @Step("Traffic chart is visible")
    public void trafficChartIsVisible() {
        trafficPage.trafficChart().shouldBe(visible);
    }

}
