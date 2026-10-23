package core.automation;

import bl.mobile.common.BaseUIElement;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.ios.IOSDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import static core.automation.AbstractDriverWrapper.Platform.IOS;

public class IOSDriverWrapper extends AbstractDriverWrapper {
    // the submit key is named Return, Search, Go or Done depending on the field, in either case
    private static final By KEYBOARD_SUBMIT_KEY = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND name IN[c] {'return', 'search', 'go', 'done'}");

    public final @Nonnull IOSDriver driver;

    public IOSDriverWrapper(
            @Nonnull String udid,
            @Nonnull String deviceName,
            @Nonnull String appConfig) {
        this(udid, deviceName, new Drivers(appConfig));
    }

    // getAppId runs before the session starts, so a missing bundle id fails fast
    private IOSDriverWrapper(
            @Nonnull String udid,
            @Nonnull String deviceName,
            @Nonnull Drivers drivers) {
        super(udid, drivers.getAppId(IOS), drivers.getIOSDriver(udid, deviceName));

        driver = (IOSDriver) super.driver;
        try {
            if (driver.isDeviceLocked()) {
                driver.unlockDevice();
            }
        } catch (RuntimeException e) {
            quitAfterFailedStart(driver, e);
            throw e;
        }
    }

    public boolean onScreen(@Nullable WebElement parent, @Nonnull By child) {
        log.trace("Check if element is displayed on screen " + child);
        try {
            findElement(parent, child);
            return true;
        } catch (NoSuchElementException exc) {
            return false;
        }
    }

    // Use instead of findElements for lists: queries element [1], [2], ... until the first miss, so
    // WebDriverAgent never snapshots the whole list. Returns at most max elements.
    public @Nonnull List<WebElement> findIosElements(
            @Nullable WebElement parent, @Nonnull By locator, int max) {
        if (max <= 0) {
            throw new IllegalArgumentException("max must be positive, got " + max);
        }

        // a class chain scoped to a parent doesn't descend into some UIKit cells; a name lookup does
        if (parent != null && isAccessibilityIdStrategy(locator)) {
            List<WebElement> all = parent.findElements(locator);
            return all.size() <= max ? all : all.subList(0, max);
        }

        IntFunction<By> indexedLocator = buildIndexedLocatorFn(locator, parent != null);
        // implicit wait 0 so the miss that ends the list returns at once
        return withImplicitWait(0, () -> {
            List<WebElement> result = new ArrayList<>(max);
            for (int i = 1; i <= max; i++) {
                List<WebElement> match = findElements(parent, indexedLocator.apply(i));
                if (match.isEmpty()) {
                    break;
                }
                result.add(match.get(0));
            }
            return result;
        });
    }

    public @Nonnull List<WebElement> findIosElements(@Nonnull BaseUIElement uiEl, int max) {
        return findIosElements(null, uiEl.by, max);
    }

    private static IntFunction<By> buildIndexedLocatorFn(@Nonnull By locator, boolean parentScoped) {
        if (!(locator instanceof By.Remotable)) {
            throw new IllegalArgumentException(
                    "Locator does not expose remote parameters: " + locator);
        }
        By.Remotable.Parameters params = ((By.Remotable) locator).getRemoteParameters();
        String strategy = params.using();
        String value = String.valueOf(params.value());

        switch (strategy) {
            case "-ios class chain":
                return i -> AppiumBy.iOSClassChain(value + "[" + i + "]");
            case "-ios predicate string":
                return i -> AppiumBy.iOSClassChain(
                        "**/*[`" + value + "`][" + i + "]");
            case "name":
            case "accessibility id":
            case "id":
                if (parentScoped) {
                    return i -> By.xpath("(.//*[@name='" + value + "'])[" + i + "]");
                }
                return i -> AppiumBy.iOSClassChain(
                        "**/*[`name == '" + escapePredicateLiteral(value) + "'`][" + i + "]");
            case "xpath":
                String prefix = parentScoped && !value.startsWith(".") ? "." : "";
                return i -> By.xpath("(" + prefix + value + ")[" + i + "]");
            default:
                throw new IllegalArgumentException(
                        "Unsupported locator strategy for indexed find: '" + strategy + "' (" + locator + ")");
        }
    }

    private static String escapePredicateLiteral(String s) {
        return s.replace("'", "''");
    }

    private static boolean isAccessibilityIdStrategy(@Nonnull By locator) {
        if (!(locator instanceof By.Remotable)) {
            return false;
        }
        String using = ((By.Remotable) locator).getRemoteParameters().using();
        return "name".equals(using) || "accessibility id".equals(using) || "id".equals(using);
    }

    public void sendKeys(@Nonnull BaseUIElement uiEl, @Nonnull String text, @Nonnull Keys closeKeyName) {
        sendKeys(null, uiEl.by, text, false);
        hideKeyboard(closeKeyName);
    }

    @Override
    public void hideKeyboardIfShown() {
        if (driver.isKeyboardShown()) {
            driver.hideKeyboard();
        }
    }

    private void hideKeyboard(Keys keyName) {
        new Actions(driver).keyDown(keyName).keyUp(keyName).perform();
    }

    // For an element whose click() silently does nothing because WDA's hit test thinks it is covered.
    public void mobileTap(@Nonnull WebElement element) {
        String elementId = ((RemoteWebElement) element).getId();
        // with elementId, x/y are offsets inside the element
        driver.executeScript("mobile: tap", Map.of("elementId", elementId, "x", 0, "y", 0));
    }

    // Text of the system alert on screen, or null if none appears within the timeout.
    public @Nullable String getAlertText(int timeOutInSeconds) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(timeOutInSeconds)).until(ExpectedConditions.alertIsPresent());
        } catch (TimeoutException e) {
            return null;
        }
        return driver.switchTo().alert().getText();
    }

    // Taps the alert's default accept button, whatever the iOS version and language call it.
    public void acceptAlert() {
        driver.executeScript("mobile: alert", Map.of("action", "accept"));
    }

    public void clickAlertButton(@Nonnull String buttonLabel) {
        driver.executeScript("mobile: alert", Map.of("action", "accept", "buttonLabel", buttonLabel));
    }

    public void terminateApp(String bundleId) {
        log.msg("Close app " + bundleId);
        driver.terminateApp(bundleId);
    }

    public void dragSlowly(int startX, int startY, int endX, int endY) {
        // a drag starting at x=0 is taken as the system back/app-switch gesture
        final int EDGE_GUARD = 20;
        startX = Math.max(startX, EDGE_GUARD);
        endX = Math.max(endX, EDGE_GUARD);
        drag(startX, startY, endX, endY, 700);
    }

    @Override
    protected void swipeOver(WebElement element, int endX, int endY) {
        Rectangle elRect = element.getRect();
        dragSlowly(elRect.x, elRect.y, endX, endY);
    }

    public void doSearchViaEnter() {
        log.msg("Tap the keyboard's submit key");
        findElement(KEYBOARD_SUBMIT_KEY).click();
    }
}
