/*
 * The MIT License
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
package org.niis.xroad.cs.test.ui.glue;

import com.codeborne.selenide.Selenide;
import io.cucumber.java.After;
import io.cucumber.java.en.Step;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v117.network.Network;
import org.openqa.selenium.devtools.v117.network.model.ConnectionType;

import java.util.Optional;

public class CommonUiStepDefs extends BaseUiStepDefs {

    @After(value = "@LoadingTesting")
    public void loadingTestingAfter() {
        var devTools = chromiumDevTools.getDevTools();
        devTools.send(Network.disable());
    }

    @Step("Page is prepared to be tested")
    public void preparePage() {
        Selenide.executeJavaScript("window.e2eTestingMode = true;\n"
                + "      const style = `\n"
                + "      <style>\n"
                + "        *, ::before, ::after {\n"
                + "            transition:none !important;\n"
                + "        }\n"
                + "      </style>`;\n"
                + "      document.head.insertAdjacentHTML('beforeend', style);");
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Step("Browser is set in {} network speed")
    public void setInBrowserSpeed(String connectionType) {
        DevTools devTools = chromiumDevTools.getDevTools();
        devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
        devTools.send(Network.emulateNetworkConditions(
                false,
                350,
                32 * 1024,
                64 * 1024,
                Optional.of(ConnectionType.fromString(connectionType))
        ));
    }
}
