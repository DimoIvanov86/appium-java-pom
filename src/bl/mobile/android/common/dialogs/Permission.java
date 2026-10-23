package bl.mobile.android.common.dialogs;

import bl.mobile.android.common.UIElement;
import bl.mobile.android.common.base.AbstractScreen;
import core.automation.DriverWrapper;
import org.openqa.selenium.By;

import javax.annotation.Nonnull;
import java.util.Arrays;

import static bl.mobile.android.common.UIElement.byBy;

/**
 * The system permission dialog. Its ids changed between Android versions, so each element is
 * looked up by all known variants. The title is logged, not compared: its wording depends on the
 * OS version, the app name and the language.
 */
public class Permission extends AbstractScreen {
    // version 1 of the Permission dialog
    private static final UIElement
            ALLOW_BTN_1 = byBy(
                By.id("com.android.packageinstaller:id/permission_allow_button"),
                "Allow",
                "'Allow' button"),
            DENY_BTN_1 = byBy(
                By.id("com.android.packageinstaller:id/permission_deny_button"),
                "Deny",
                "'Deny' option");
    private static final By
            TITLE_BY_1 = By.id("com.android.packageinstaller:id/permission_message");

    // version 2 of the Permission dialog
    private static final UIElement
            ALLOW_BTN_2 = byBy(
                By.id("com.android.permissioncontroller:id/permission_allow_foreground_only_button"),
                "While using the app",
                "'While using the app' option"),
            DENY_BTN_2 = byBy(
                By.id("com.android.permissioncontroller:id/permission_deny_button"),
                "",  // may have "Deny" or "Don't allow" values
                "'Deny' option");
    private static final By
            TITLE_BY_2 = By.id("com.android.permissioncontroller:id/permission_message");

    // version 2.1 of the Permission dialog
    private static final UIElement
            ALLOW_BTN_3 = byBy(
                By.id("com.android.permissioncontroller:id/permission_allow_button"),
                "Allow",
                "'Allow' option");

    // version 3.3 of the Permission dialog
    private static final UIElement
            ALLOW_BTN_4 = byBy(
                By.id("com.android.permissioncontroller:id/permission_allow_all_button"),
                "Allow all",
                "'Allow all' option");

    private static final int TIMEOUT_IN_SEC = 5;

    private final UIElement title, allowButton, denyButton;

    /**
     * @param permission what the dialog asks for (e.g. "camera"), used in log messages only
     * @throws org.openqa.selenium.NoSuchElementException if no permission dialog is shown
     */
    public Permission(
            @Nonnull String permission,
            @Nonnull DriverWrapper driverWrapper) {
        super(driverWrapper);

        By titleBy = driverWrapper.findBy(Arrays.asList(TITLE_BY_1, TITLE_BY_2), TIMEOUT_IN_SEC);
        this.title = byBy(
                titleBy,
                "",
                String.format("'%s' permission dialog", permission));
        this.allowButton = (UIElement) driverWrapper.findUIElement(
                null,
                Arrays.asList(ALLOW_BTN_1, ALLOW_BTN_2, ALLOW_BTN_3, ALLOW_BTN_4),
                TIMEOUT_IN_SEC);
        this.denyButton = (UIElement) driverWrapper.findUIElement(
                null,
                Arrays.asList(DENY_BTN_1, DENY_BTN_2),
                TIMEOUT_IN_SEC);

        assertDisplayed();
    }

    public void clickAllow() {
        driverWrapper.click(allowButton);
    }

    public void clickDeny() {
        driverWrapper.click(denyButton);
    }

    public static boolean isOnScreen(DriverWrapper driverWrapper) {
        return driverWrapper.onScreen(Arrays.asList(TITLE_BY_1, TITLE_BY_2));
    }

    /**
     * Verifies the UI of the dialog - its title and both buttons
     */
    @Override
    public void verify() {
        log.startBlock(String.format("Verify %s", title.purpleDescr));
        verify(title);
        verify(allowButton);
        verify(denyButton);
        log.endBlock();
    }

    @Override
    public void assertDisplayed() {
        assertDisplayed(String.format("%s has been displayed", title.purpleDescr));
    }

    @Override
    public boolean isDisplayed() {
        return true; // if all elements are successfully initialized (in constructor), the dialog is displayed
    }
}
