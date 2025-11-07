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
package org.niis.xroad.common.test.ui.page;

import com.codeborne.selenide.SelenideElement;
import org.niis.xroad.common.test.ui.page.component.Dialog;
import org.niis.xroad.common.test.ui.page.component.Menu;
import org.niis.xroad.common.test.ui.page.component.SubMenu;

import static com.codeborne.selenide.Selenide.$x;
import static java.lang.String.format;

/**
 * Common page objects which can be found in any page.
 * @param <M> menu type
 * @param <S> sub menu type
 */
@SuppressWarnings("InnerClassMayBeStatic")
public class AbstractCommonPageObj<M extends Menu, S extends SubMenu> {
    public final Dialog dialog = new Dialog();
    public final M menu;
    public final S subMenu;
    public final SnackBar snackBar = new SnackBar();
    public final Alerts alerts = new Alerts();
    public final Form form = new Form();
    public final ElevatedView elevatedView = new ElevatedView();

    public AbstractCommonPageObj(M menu, S subMenu) {
        this.menu = menu;
        this.subMenu = subMenu;
    }

    public class Form {

        public SelenideElement inputErrorMessage() {
            return $x("//div[contains(@class, 'v-messages__message')]");
        }

        public SelenideElement inputErrorMessage(String message) {
            return $x(format("//div[contains(@class, 'v-messages__message') and text()='%s']", message));
        }
    }

    public class SnackBar {
        public SelenideElement success() {
            return $x("//div[@data-test='success-snackbar']");
        }

        public SelenideElement btnClose() {
            return $x("//button[@data-test='close-snackbar']");
        }
    }

    public class Alerts {
        public SelenideElement alert(final String text) {
            return $x(format("//div[@data-test='contextual-alert']//p[contains(text(), '%s')]", text));
        }

        public SelenideElement btnClose() {
            return $x("//div[@data-test='contextual-alert']//button[@data-test='close-alert']");
        }
    }

    public class ElevatedView {
        public SelenideElement self() {
            return $x("//div[contains(@class, 'xrd-elevated-view')]");
        }

        public SelenideElement btnClose() {
            return self().$x(".//button[@data-test='close-x']");
        }
    }
}


