package core.automation;

import bl.mobile.common.BaseUIElement;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.nativekey.AndroidKey;
import io.appium.java_client.android.nativekey.KeyEvent;
import io.appium.java_client.android.nativekey.KeyEventFlag;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;

import static core.automation.AbstractDriverWrapper.Platform.ANDROID;
import static core.reports.HtmlFormatter.Color.PURPLE;

public class DriverWrapper extends AbstractDriverWrapper {

    public final @Nonnull AndroidDriver driver;
    public final @Nonnull ADB adb;

    public DriverWrapper(
            @Nonnull String udid,
            @Nonnull String port,
            @Nonnull String deviceName,
            @Nonnull String appConfig) {
        this(udid, port, deviceName, appConfig, true);
    }

    public DriverWrapper(
            @Nonnull String udid,
            @Nonnull String port,
            @Nonnull String deviceName,
            @Nonnull String appConfig,
            boolean resetOnStart) {
        this(udid, port, deviceName, new Drivers(appConfig), resetOnStart);
    }

    // getAppId runs before the session starts, so a missing app id fails fast
    private DriverWrapper(
            @Nonnull String udid,
            @Nonnull String port,
            @Nonnull String deviceName,
            @Nonnull Drivers drivers,
            boolean resetOnStart) {
        super(udid, drivers.getAppId(ANDROID), drivers.getAndroidDriver(udid, port, deviceName, resetOnStart));

        driver = (AndroidDriver) super.driver;
        this.adb = new ADB(udid);
        try {
            if (driver.isDeviceLocked()) {
                driver.unlockDevice();
            }
        } catch (RuntimeException e) {
            quitAfterFailedStart(driver, e);
            throw e;
        }
    }

    public void typeWithKeyboard(BaseUIElement textField, String searchText) {
        click(textField);
        for (char ch : searchText.toCharArray()) {
            AndroidKey key;
            try {
                key = AndroidKey.valueOf(String.valueOf(ch).toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("No key event for character '" + ch + "' in: " + searchText, e);
            }
            pressKey(key);
        }
    }

    public void hideKeyboardIfShown() {
        if (!driver.isKeyboardShown()) {
            return;
        }
        log.trace("[Hide keyboard] Keyboard opened - try hideKeyboard()");
        driver.hideKeyboard();

        if (!driver.isKeyboardShown()) {
            return;
        }
        log.trace("[Hide keyboard] Keyboard still opened - try AndroidKey.BACK");
        pressKey(AndroidKey.BACK);

        if (!driver.isKeyboardShown()) {
            return;
        }
        log.trace("[Hide keyboard] Keyboard still opened - try navigate().back()");
        driver.navigate().back();

        if (!driver.isKeyboardShown()) {
            return;
        }
        throw new IllegalStateException("Cannot close keyboard");
    }

    public void hideKeyboardIfShownUsingBack() {
        if (!driver.isKeyboardShown()) {
            return;
        }
        log.debug("[Hide keyboard] Keyboard opened - use AndroidKey.BACK");
        pressKey(AndroidKey.BACK);
    }

    public void pressKey(AndroidKey key) {
        log.debug("Press key " + key);
        driver.pressKey(new KeyEvent(key));
    }

    public void clickDeviceBackButton() {
        log.msg("Click android 'Back' button");
        driver.pressKey(new KeyEvent(AndroidKey.BACK));
    }

    public void clickEnterButton() {
        log.msg("Click android 'Enter' button");
        driver.pressKey(new KeyEvent(AndroidKey.ENTER));
    }

    public void clickTabButton() {
        log.msg("Click android 'Tab' button");
        driver.pressKey(new KeyEvent(AndroidKey.TAB));
    }

    public void clickHomeButton() {
        log.msg("Click android 'Home' button");
        driver.pressKey(new KeyEvent(AndroidKey.HOME));
    }

    public void clickSearch() {
        log.msg("Click android 'Search' button");
        driver.pressKey(new KeyEvent(AndroidKey.SEARCH));
    }

    // works only if the field listens for the Enter key
    public void doSearchViaEnter() {
        log.msg("Click Enter to perform search");
        driver.pressKey(new KeyEvent(AndroidKey.ENTER)
                .withFlag(KeyEventFlag.SOFT_KEYBOARD)
                .withFlag(KeyEventFlag.KEEP_TOUCH_MODE)
                .withFlag(KeyEventFlag.EDITOR_ACTION));
    }

    public void lockDevice() {
        log.msg("Lock device screen");
        driver.lockDevice();
    }

    public void unlockDevice() {
        log.msg("Unlock device screen");
        driver.unlockDevice();
    }

    public void restartApp() {
        log.startBlock("Restart " + PURPLE.format(appId) + " app");
        terminateApp(appId);
        activateApp(appId);
        log.endBlock();
    }

    public void resetApp() {
        log.startBlock("Reset " + PURPLE.format(appId) + " app");
        terminateApp(appId);
        adb.clearAppData(appId);
        activateApp(appId);
        log.endBlock();
    }

    public void resetApp(String bundleId) {
        log.startBlock("Reset " + PURPLE.format(bundleId) + " app");
        terminateApp(bundleId);
        adb.clearAppData(bundleId);
        log.endBlock();
    }

    public void terminateApp(String bundleId) {
        log.msg("Close app " + bundleId);
        try {
            driver.executeScript("mobile: terminateApp", Map.of("appId", bundleId, "timeout", 5000));
        } catch (Exception e) {
            log.msg("Appium terminateApp failed, falling back to ADB force-stop: " + e.getMessage());
            adb.stopApp(bundleId);
        }
    }

    public void activateApp(String bundleId) {
        log.msg("Bring to foreground " + bundleId);
        driver.activateApp(bundleId);
    }

    public void runAppInBackground(int milliseconds) {
        log.msg("Run app in background for " + milliseconds + " millis");
        driver.runAppInBackground(Duration.ofMillis(milliseconds));
    }

    public void openNotifications() {
        driver.openNotifications();
    }

    public boolean onScreen(@Nullable WebElement parent, @Nonnull By child) {
        log.trace("Check if element is displayed on screen " + child);
        try {
            return findElement(parent, child).isDisplayed();
        } catch (NoSuchElementException exc) {
            return false;
        }
    }
}
