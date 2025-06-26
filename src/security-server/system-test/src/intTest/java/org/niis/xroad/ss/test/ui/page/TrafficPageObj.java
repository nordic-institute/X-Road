package org.niis.xroad.ss.test.ui.page;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$x;

public class TrafficPageObj {

    public SelenideElement trafficChart() {
        return $x("//div[@data-test='traffic-chart']");
    }
}
