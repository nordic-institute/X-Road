package org.niis.xroad.test.ui.glue.constants;

import org.openqa.selenium.By;

import static org.openqa.selenium.By.xpath;

public class Constants {
    public static final By BTN_DIALOG_SAVE = xpath("//button[@data-test=\"dialog-save-button\"]");
    public static final By SNACKBAR_SUCCESS = xpath("//div[@data-test=\"success-snackbar\"]");
    public static final By BTN_CLOSE_SNACKBAR = By.xpath("//button[@data-test=\"close-snackbar\"]");
}
