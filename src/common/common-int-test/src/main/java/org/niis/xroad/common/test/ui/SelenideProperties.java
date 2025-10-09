/**
 * Copyright (c) 2022 Nortal AS
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.niis.xroad.common.test.ui;

import com.codeborne.selenide.AssertionMode;
import com.codeborne.selenide.FileDownloadMode;
import com.codeborne.selenide.SelectorMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@SuppressWarnings("checkstyle:MagicNumber")
@ConfigurationProperties(prefix = "test-automation.selenide")
public class SelenideProperties {

    private boolean enabled = false;

    /**
     * Timeout in milliseconds to fail the test, if conditions still not met
     * Can be configured either programmatically, via selenide.properties file or by system property "-Dselenide.timeout=10000"
     * <br>
     * Default value: 4000 (milliseconds)
     */
    private Long timeout = 8000L;

    /**
     * Interval in milliseconds, when checking if a single element or collection elements are appeared
     * Can be configured either programmatically, via selenide.properties file or by system property "-Dselenide.pollingInterval=50"
     * <br>
     * Default value: 200 (milliseconds)
     */
    private Long pollingInterval = 100L;

    /**
     * Should Selenide re-spawn browser if it's disappeared (hangs, broken, unexpectedly closed).
     * <br>
     * Can be configured either programmatically, via selenide.properties file
     * or by system property "-Dselenide.reopenBrowserOnFail=false".
     * <br>
     * Set this property to false if you want to disable automatic re-spawning the browser.
     * <br>
     * Default value: true
     */
    private Boolean reopenBrowserOnFail = true;

    /**
     * URL of remote web driver (in case of using Selenium Grid).
     * Can be configured either programmatically, via selenide.properties file
     * or by system property "-Dselenide.remote=http://localhost:5678/wd/hub".
     * <br>
     * Default value: null (Grid is not used).
     */
    private String remote;

    /**
     * The browser window size.
     * Can be configured either programmatically, via selenide.properties file or by system property "-Dselenide.browserSize=1024x768".
     * <br>
     * Default value: 1920x1080
     */
    private String browserSize = "1920x1080";

    /**
     * Chrome options that are set on chrome based driver factory
     */
    private String chromeOptionsArgs = "--guest,"
            // Disable Google services & networking
            + "--disable-sync,"
            + "--disable-background-networking,"
            + "--disable-features=SafeBrowsing,"
            + "--disable-client-side-phishing-detection,"
            + "--disable-component-update,"
            + "--disable-domain-reliability,"
            + "--disable-features=OptimizationGuideModelDownloading,OptimizationHintsFetching,"
            + "OptimizationTargetPrediction,OptimizationHints,"
            // UI/UX improvements
            + "--no-first-run,"
            + "--no-default-browser-check,"
            + "--disable-search-engine-choice-screen,"
            + "--disable-default-apps,"
            + "--disable-extensions,"
            + "--disable-translate,"
            // Test stability
            + "--disable-popup-blocking,"
            + "--disable-prompt-on-repost,"
            + "--password-store=basic,"
            + "--use-mock-keychain,";

    /**
     * Should webdriver wait until page is completely loaded.
     * Possible values: "none", "normal" and "eager".
     * <br>
     * Can be configured either programmatically, via selenide.properties file or by system property "-Dselenide.pageLoadStrategy=eager".
     * Default value: "normal".
     * <br>
     * - `normal`: return after the load event fires on the new page (it's default in Selenium webdriver);
     * - `eager`: return after DOMContentLoaded fires;
     * - `none`: return immediately
     * <br>
     * In some cases `eager` can bring performance boosts for the slow tests.
     * Though, we left default value `normal` because we are afraid to break users' existing tests.
     * <br>
     * See https://w3c.github.io/webdriver/webdriver-spec.html#dfn-page-loading-strategy
     *
     * @since 3.5
     */
    private String pageLoadStrategy = "normal";

    /**
     * Timeout for loading a web page (in milliseconds).
     * Default timeout in Selenium WebDriver is 300 seconds (which is incredibly long).
     * Selenide default is 30 seconds.
     *
     * @since 5.15.0
     */
    private Long pageLoadTimeout = 20000L;

    /**
     * ATTENTION! Automatic WebDriver waiting after click isn't working in case of using this feature.
     * Use clicking via JavaScript instead common element clicking.
     * This solution may be helpful for testing in Internet Explorer.
     * Can be configured either programmatically, via selenide.properties file or by system property "-Dselenide.clickViaJs=true".
     * <br>
     * Default value: false
     */
    private Boolean clickViaJs = false;

    /**
     * Defines if Selenide takes screenshots on failing tests.
     * Can be configured either programmatically, via selenide.properties file or by system property "-Dselenide.screenshots=false".
     * <br>
     * Default value: true
     */
    private Boolean screenshots = true;

    /**
     * Defines if Selenide saves page source on failing tests.
     * Can be configured either programmatically, via selenide.properties file or by system property "-Dselenide.savePageSource=false".
     * <br>
     * Default value: true
     */
    private Boolean savePageSource = true;

    /**
     * Folder to store screenshots to.
     * Can be configured either programmatically, via selenide.properties file
     * or by system property "-Dselenide.reportsFolder=test-result/reports".
     * <br>
     * Default value: "build/reports/tests" (this is default for Gradle projects)
     */
    private String reportsFolder = "build/reports/test-automation/selenide-failures";

    /**
     * Folder to store downloaded files to.
     * Can be configured either programmatically, via selenide.properties file
     * or by system property "-Dselenide.downloadsFolder=test-result/downloads".
     * <br>
     * Default value: "build/downloads" (this is default for Gradle projects)
     */
    private String downloadsFolder = "build/reports/test-automation/selenide-downloads";

    /**
     * If set to true, sets value by javascript instead of using Selenium built-in "sendKey" function
     * (that is quite slow because it sends every character separately).
     * <br>
     * Tested on Codeborne projects - works well, speed up ~30%.
     * Some people reported 150% speedup (because sending characters one-by-one was especially
     * slow via network to Selenium Grid on cloud).
     * <br>
     * https://github.com/selenide/selenide/issues/135
     * Can be configured either programmatically, via selenide.properties file or by system property "-Dselenide.fastSetValue=true".
     * <br>
     * Default value: false
     */
    private Boolean fastSetValue = false;

    /**
     *
     * Choose how Selenide should retrieve web elements: using default CSS or Sizzle (CSS3).
     * <br>
     * Can be configured either programmatically, via selenide.properties file or by system property "-Dselenide.selectorMode=Sizzle".
     *
     * <br>
     * Possible values: "CSS" or "Sizzle"
     * <br>
     * Default value: CSS
     *
     * @see SelectorMode
     */
    private SelectorMode selectorMode = SelectorMode.CSS;

    /**
     * Assertion mode
     * Can be configured either programmatically, via selenide.properties file
     * or by system property "-Dselenide.assertionMode=SOFT".
     *
     * <br>
     * Possible values: "STRICT" or "SOFT"
     * <br>
     * Default value: STRICT
     *
     * @see AssertionMode
     */
    private AssertionMode assertionMode = AssertionMode.STRICT;

    /**
     * Defines if files are downloaded via direct HTTP or vie selenide embedded proxy server
     * Can be configured either programmatically, via selenide.properties file or by system property "-Dselenide.fileDownload=PROXY"
     * <br>
     * Default: HTTPGET
     */
    private FileDownloadMode fileDownload = FileDownloadMode.HTTPGET;

    /**
     * If Selenide should run browser through its own proxy server.
     * It allows some additional features which are not possible with plain Selenium.
     * But it's not enabled by default because sometimes it would not work (more exactly, if tests and browser and
     * executed on different machines, and "test machine" is not accessible from "browser machine"). If it's not your
     * case, I recommend to enable proxy.
     * Can be configured either programmatically, via selenide.properties file or by system property "-Dselenide.proxyEnabled=true"
     * <br>
     * Default: false
     */
    private Boolean proxyEnabled = false;

    /**
     * Host of Selenide proxy server.
     * Used only if proxyEnabled == true.
     * Can be configured either programmatically, via selenide.properties file or by system property "-Dselenide.proxyHost=127.0.0.1"
     * <br>
     * Default: empty (meaning that Selenide will detect current machine's ip/hostname automatically)
     *
     * @see com.browserup.bup.client.ClientUtil.getConnectableAddress
     */
    private String proxyHost = "127.0.0.1";

    /**
     * Port of Selenide proxy server.
     * Used only if proxyEnabled == true.
     * Can be configured either programmatically, via selenide.properties file or by system property "-Dselenide.proxyPort=8888"
     * <br>
     * Default: 0 (meaning that Selenide will choose a random free port on current machine)
     */
    private Integer proxyPort = 0;

    /**
     * Whether webdriver logs should be enabled.
     * These logs may be useful for debugging some webdriver issues.
     * But in most cases they are not needed (and can take quite a lot of disk space),
     * that's why don't enable them by default.
     * Default: false
     *
     * @since 5.18.0
     */
    private Boolean webdriverLogsEnabled = false;

    /**
     * Enables the ability to run the browser in headless mode.
     * Works only for Chrome(59+) and Firefox(56+).
     * Can be configured either programmatically, via selenide.properties file or by system property "-Dselenide.headless=true"
     * <br>
     * Default: false
     */
    private Boolean headless = true;

}
