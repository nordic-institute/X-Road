package org.niis.xroad.test.ui.page;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$$x;
import static com.codeborne.selenide.Selenide.$x;

public class DiagnosticsPageObj {

    public SelenideElement backupEncryptionStatus() {
        return $x("//div[@data-test='backup-encryption-status']");
    }

    public ElementsCollection backupEncryptionKeyList() {
        return $$x("//table[@data-test='backup-encryption-keys']/tbody/tr/td");
    }

    public SelenideElement messageLogEncryptionStatus() {
        return $x("//div[@data-test='message-log-archive-encryption-status']");
    }

    public SelenideElement messageLogDatabaseEncryptionStatus() {
        return $x("//div[@data-test='message-log-database-encryption-status']");
    }

    public SelenideElement memberMessageLogEncryptionKey() {
        return $x("//table[@data-test='member-encryption-status']")
                .$("td.status-wrapper:not(:has(i.warning-icon))");
    }

    public SelenideElement memberMessageLogEncryptionKeyWithWarning() {
        return $x("//table[@data-test='member-encryption-status']")
                .$("td.status-wrapper:has(i.warning-icon)");
    }
}
