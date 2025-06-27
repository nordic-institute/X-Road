package org.niis.xroad.ss.test.ui.page;

import com.codeborne.selenide.SelenideElement;
import org.niis.xroad.common.test.ui.utils.VuetifyHelper.Select;

import static com.codeborne.selenide.Selenide.$x;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vSelect;

public class TrafficPageObj {

    public final Filter filter = new Filter();

    public SelenideElement trafficChart() {
        return $x("//div[@class='vue-apexcharts']");
    }

    public static class Filter {
        public Select selectClient() {
            return vSelect($x("//div[@data-test='select-client']"));
        }

        public Select selectService() {
            return vSelect($x("//div[@data-test='select-service']"));
        }

        public Select selectExchangeRole() {
            return vSelect($x("//div[@data-test='select-exchangeRole']"));
        }

        public Select selectStatus() {
            return vSelect($x("//div[@data-test='select-status']"));
        }
    }
}
