package core.automation;

import bl.mobile.common.BaseAbstractScreen;
import bl.mobile.common.BaseUIElement;
import core.automation.selfhealing.LocatorHealer;
import core.reports.Log;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.InvalidArgumentException;
import org.openqa.selenium.InvalidElementStateException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Point;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import tools.utils.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static core.automation.AbstractDriverWrapper.Direction.DOWN;
import static core.automation.AbstractDriverWrapper.Direction.LEFT;
import static core.automation.AbstractDriverWrapper.Direction.RIGHT;
import static core.automation.AbstractDriverWrapper.Direction.UP;
import static core.reports.HtmlFormatter.Color.PURPLE;

/**
 * All Appium calls go through here, so every step is logged and every wait is explicit.
 */
public abstract class AbstractDriverWrapper {
    public enum Direction {
        UP,
        DOWN,
        LEFT,
        RIGHT
    }

    public enum Platform {
        ANDROID,
        IOS
    }

    public static final Clock CLOCK = Clock.systemUTC();
    public static final int IMPLICIT_WAIT = 3;
    public static final int SHORT_WAIT = 5;
    public static final int AVERAGE_WAIT = 10;
    public static final int LONG_WAIT = 15;
    public static final int LONGER_WAIT = 20;
    public static final int LONGEST_WAIT = 30;
    private static final int INTERVAL_DELAY = 300;
    private static final String TRUE = "true";
    private static final String CHECKED = "checked";

    public final @Nonnull Log log;
    public final @Nonnull String udid;
    // package name (Android) or bundle id (iOS) of the app under test
    public final @Nonnull String appId;
    public final @Nonnull AppiumDriver driver;
    private int implicitWait;

    protected AbstractDriverWrapper(
            @Nonnull String udid,
            @Nonnull String appId,
            @Nonnull AppiumDriver driver) {
        this.udid = udid;
        this.appId = appId;
        this.driver = driver;
        try {
            this.log = new Log(driver);
            setImplicitWait(IMPLICIT_WAIT);
        } catch (RuntimeException e) {
            quitAfterFailedStart(driver, e);
            throw e;
        }
    }

    // The session is already open on the device; leaving it would block the next one until it times out.
    static void quitAfterFailedStart(@Nonnull AppiumDriver driver, @Nonnull RuntimeException cause) {
        try {
            driver.quit();
        } catch (RuntimeException quitFailure) {
            cause.addSuppressed(quitFailure);
        }
    }

    public @Nonnull WebElement findElement(@Nonnull By by) {
        return findElement(null, by);
    }

    public @Nonnull WebElement findElement(@Nonnull BaseUIElement uiEl) {
        log.trace("Search for " + uiEl.description);
        try {
            return findElement(null, uiEl.by);
        } catch (NoSuchElementException e) {
            return LocatorHealer.tryHeal(this, null, uiEl, e);
        }
    }

    public @Nonnull WebElement findElement(@Nonnull List<By> any) {
        return findElement(null, any);
    }

    public WebElement findElement(@Nullable WebElement parent, @Nonnull BaseUIElement child) {
        log.trace("Search for " + child.description);
        try {
            return findElement(parent, child.by);
        } catch (NoSuchElementException e) {
            return LocatorHealer.tryHeal(this, parent, child, e);
        }
    }

    public WebElement findElement(@Nullable WebElement parent, @Nonnull By child) {
        log.trace("Search for " + child + " with implicit wait " + implicitWait);
        return (parent != null)
                ? parent.findElement(child)
                : driver.findElement(child);
    }

    public @Nonnull WebElement findElement(@Nullable WebElement parent, @Nonnull List<By> any) {
        return findElement(parent, any, IMPLICIT_WAIT);
    }

    /**
     * Returns the first element matched by any of the locators, polling until the timeout. For an
     * element that OS versions or A/B variants of the app expose under different locators.
     *
     * @throws NoSuchElementException if none of the locators matches in time
     */
    public @Nonnull WebElement findElement(
            @Nullable WebElement parent,
            @Nonnull List<By> any,
            int timeOutInSeconds) {
        log.trace("Search for element with locators " + Utils.toString(any));
        WebElement element = pollFor(() -> findFirst(parent, any), timeOutInSeconds);
        if (element == null) {
            throw new NoSuchElementException("Could not locate element with any of the locators " + Utils.toString(any));
        }
        return element;
    }

    public @Nonnull By findBy(@Nonnull List<By> any, int timeOutInSeconds) {
        log.trace("Search for element with locators " + Utils.toString(any));
        By found = pollFor(() -> firstMatchingBy(any), timeOutInSeconds);
        if (found == null) {
            throw new NoSuchElementException("Could not locate element with any of the locators " + Utils.toString(any));
        }
        return found;
    }

    // Polls the raw locators without self-healing: the list of variants is itself the fallback, and a
    // missing optional element (e.g. a dialog that isn't shown) must not trigger an API call per variant.
    public @Nonnull BaseUIElement findUIElement(
            @Nullable WebElement parent,
            @Nonnull List<? extends BaseUIElement> any,
            int timeOutInSeconds) {
        BaseUIElement found = pollFor(() -> firstMatchingUIElement(parent, any), timeOutInSeconds);
        if (found == null) {
            throw new NoSuchElementException(
                    "Could not locate element with any of the locators " + Utils.toString(toBys(any)));
        }
        return found;
    }

    // Unlike findElement, findElements returns an empty list instead of throwing.
    public @Nonnull List<WebElement> findElements(@Nonnull By by) {
        return findElements(null, by);
    }

    public @Nonnull List<WebElement> findElements(@Nonnull BaseUIElement uiEl) {
        List<WebElement> result = findElements(null, uiEl.by);
        if (result.isEmpty()) {
            By healedBy = LocatorHealer.getHealedBy(uiEl);
            if (healedBy != null) {
                result = findElements(null, healedBy);
            }
            if (result.isEmpty()) {
                healedBy = LocatorHealer.tryHealLocator(this, null, uiEl);
                if (healedBy != null) {
                    result = findElements(null, healedBy);
                }
            }
        }
        return result;
    }

    public @Nonnull List<WebElement> findElements(@Nonnull List<By> any) {
        return findElements(null, any);
    }

    public @Nonnull List<WebElement> findElements(@Nonnull BaseUIElement parent, @Nonnull BaseUIElement child) {
        log.trace("Search for " + child.description);
        return findElements(findElement(parent), child);
    }

    public @Nonnull List<WebElement> findElements(@Nullable WebElement parent, @Nonnull BaseUIElement child) {
        log.trace("Search for " + child.description);
        List<WebElement> result = findElements(parent, child.by);
        if (result.isEmpty()) {
            By healedBy = LocatorHealer.getHealedBy(child);
            if (healedBy != null) {
                result = findElements(parent, healedBy);
            }
            if (result.isEmpty()) {
                healedBy = LocatorHealer.tryHealLocator(this, parent, child);
                if (healedBy != null) {
                    result = findElements(parent, healedBy);
                }
            }
        }
        return result;
    }

    public List<WebElement> findElements(@Nullable WebElement parent, @Nonnull By child) {
        log.trace("Search for element with locator " + child + " and implicit wait " + implicitWait);
        return (parent != null)
                ? parent.findElements(child)
                : driver.findElements(child);
    }

    public @Nonnull List<WebElement> findElements(@Nonnull By by, int timeOutInSeconds) {
        log.trace("Search for elements with locator " + by + " and timeout " + timeOutInSeconds);
        List<WebElement> elements = pollFor(() -> nonEmptyOrNull(findElements(null, by)), timeOutInSeconds);
        return elements == null ? Collections.emptyList() : elements;
    }

    public @Nonnull List<WebElement> findElements(@Nonnull WebElement parent, @Nonnull List<By> any) {
        return findElements(parent, any, IMPLICIT_WAIT);
    }

    // Returns the elements of the first locator that matches anything.
    public @Nonnull List<WebElement> findElements(
            @Nonnull WebElement parent,
            @Nonnull List<By> any,
            int timeOutInSeconds) {
        log.trace("Search for elements with locators " + Utils.toString(any));
        List<WebElement> elements = pollFor(() -> firstNonEmptyResult(parent, any), timeOutInSeconds);
        return elements == null ? Collections.emptyList() : elements;
    }

    public boolean onScreen(@Nonnull By by) {
        return onScreen(null, by);
    }

    public abstract boolean onScreen(@Nullable WebElement parent, @Nonnull By child);

    public boolean onScreen(@Nonnull List<By> any) {
        return onScreen(null, any);
    }

    public boolean onScreen(@Nonnull WebElement parent, @Nonnull List<By> any) {
        log.trace("Check if element on screen can be identified by any of " + Utils.toString(any));
        try {
            return findElement(parent, any).isDisplayed();
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    public boolean onScreen(@Nonnull BaseUIElement element) {
        log.trace("Check if " + element.description + " is displayed");
        return onScreen(null, element);
    }

    public boolean onScreen(@Nullable WebElement parent, @Nonnull BaseUIElement child) {
        log.trace("Check if element is displayed on screen " + child.purpleDescr);
        if (onScreen(parent, child.by)) {
            return true;
        }
        // a presence check must stay fast, so only already-healed locators are tried here
        By healedBy = LocatorHealer.getHealedBy(child);
        if (healedBy != null) {
            return onScreen(parent, healedBy);
        }
        return false;
    }

    public boolean onScreenByUiElList(@Nonnull List<? extends BaseUIElement> any) {
        List<By> childrenBys = any
                .stream()
                .map(el -> el.by)
                .collect(Collectors.toList());
        return onScreen(childrenBys);
    }

    public boolean isFullyDisplayedBy(WebElement parent, List<By> children) {
        log.trace("Verify all elements from the list are displayed on the screen " + Utils.toString(children));

        for (By child : children) {
            if (!onScreen(parent, child)) {
                return false;
            }
        }

        return true;
    }

    public boolean isFullyDisplayed(WebElement parent, List<? extends BaseUIElement> children) {
        List<By> childrenBys = children
                .stream()
                .map(el -> el.by)
                .collect(Collectors.toList());

        return isFullyDisplayedBy(parent, childrenBys);
    }

    // Polls the condition until it holds or the timeout passes; returns whether it held.
    public boolean waitUntil(@Nonnull BooleanSupplier condition, int timeOutInSeconds) {
        return pollFor(() -> condition.getAsBoolean() ? Boolean.TRUE : null, timeOutInSeconds) != null;
    }

    public WebElement waitToAppear(By by, long timeOutInSec) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeOutInSec));
        wait.until(ExpectedConditions.visibilityOfElementLocated(by));

        return findElement(by);
    }

    public WebElement waitToAppear(BaseUIElement element, long timeOutInSec) {
        log.msg(String.format("Wait for %s to appear...", element.purpleDescr));
        try {
            return waitToAppear(element.by, timeOutInSec);
        } catch (TimeoutException e) {
            By healedBy = LocatorHealer.getHealedBy(element);
            if (healedBy != null) {
                return waitToAppear(healedBy, timeOutInSec);
            }
            throw e;
        }
    }

    public void waitToAppear(@Nonnull List<By> any, int totalTimeoutSeconds) {
        log.trace("Wait for any of the elements to appear for " + totalTimeoutSeconds + " sec "
                + Utils.toString(any));
        WebElement shown = pollFor(() -> displayedOrNull(findFirst(null, any)), totalTimeoutSeconds);
        if (shown == null) {
            throw new TimeoutException("None of the elements appeared, locators: " + Utils.toString(any));
        }
    }

    public void waitToDisappear(By by, long timeOutInSec) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeOutInSec));
        wait.until(ExpectedConditions.invisibilityOfElementLocated(by));
    }

    public void waitToDisappear(BaseUIElement element, long timeOutInSec) {
        log.msg(String.format("Wait for %s to disappear...", element.purpleDescr));
        waitToDisappear(element.by, timeOutInSec);
    }

    public void click(BaseUIElement uiEl) {
        click(null, uiEl);
    }

    public void click(@Nullable WebElement parent, @Nonnull BaseUIElement child) {
        log.msg("Click " + child.purpleDescr);
        findElement(parent, child).click();
    }

    public void click(@Nonnull List<By> any) {
        click(null, any);
    }

    public void click(@Nullable WebElement parent, @Nonnull List<By> any) {
        log.trace("Click on element found by one of locators " + Utils.toString(any));
        boolean found = false;
        for (By by : any) {
            try {
                findElement(parent, by).click();
                found = true;
                break;
            } catch (NoSuchElementException e) {
                // try the next locator
            }
        }
        if (!found) {
            throw new NoSuchElementException("Element not located by any of locators " + Utils.toString(any));
        }
    }

    public void clickByUiElList(@Nonnull List<? extends BaseUIElement> any) {
        BaseUIElement uiEl = findUIElement(null, any, IMPLICIT_WAIT);
        click(uiEl);
    }

    public void tapScreen() {
        Dimension size = getScreenSize();
        int x = size.width / 2;
        int y = size.height / 2;

        log.msg("Tap in the middle of the screen (" + x + "," + y + ")");
        drag(x, y, x, y);
    }

    public void tapOnCoordinates(int x, int y) {
        log.msg("Tap on coordinates (" + x + "," + y + ") ");
        drag(x, y, x, y, 100);
    }

    public void tapMiddleOfElement(@Nonnull BaseUIElement element) {
        log.msg("Tap the middle of the " + element.description);
        tapMiddleOfElement(findElement(element));
    }

    public void tapMiddleOfElement(@Nonnull WebElement element) {
        Rectangle elRect = element.getRect();

        int centerX = elRect.getX() + elRect.getWidth() / 2;
        int centerY = elRect.getY() + elRect.getHeight() / 2;

        tapOnCoordinates(centerX, centerY);
    }

    public void longPress(WebElement el) {
        log.msg("Long-press for 3 seconds on an element");
        Actions actions = new Actions(driver);
        actions.clickAndHold(el)
                .pause(Duration.ofSeconds(3))
                .release(el)
                .perform();
    }

    public @Nonnull String getText(@Nonnull By by) {
        return getText(null, by);
    }

    public String getText(BaseUIElement uiEl) {
        return getText(null, uiEl);
    }

    public @Nonnull String getText(@Nullable WebElement parent, @Nonnull By child) {
        log.trace("Get text of element " + child);
        return findElement(parent, child).getText();
    }

    public @Nonnull String getText(@Nullable WebElement parent, @Nonnull BaseUIElement child) {
        log.trace(child.description + " getText");
        return findElement(parent, child).getText();
    }

    public @Nonnull String getText(@Nonnull List<By> any) {
        return getText(null, any);
    }

    public @Nonnull String getText(@Nonnull WebElement parent, @Nonnull List<By> any) {
        log.trace("Get text of element " + Utils.toString(any));
        return findElement(parent, any).getText();
    }

    public void sendKeys(@Nullable WebElement parent, @Nonnull By child, @Nonnull String text) {
        sendKeys(parent, child, text, true);
    }

    public void sendKeys(@Nullable WebElement parent, @Nonnull By child, @Nonnull String text, boolean hideKeyboard) {
        log.trace("Attempt to send text " + PURPLE.format(text) + " to " + child);
        WebElement field = findElement(parent, child);
        field.clear();
        field.sendKeys(text);
        if (hideKeyboard) {
            hideKeyboardIfShown();
        }
    }

    public abstract void hideKeyboardIfShown();

    public void sendKeys(@Nonnull List<? extends BaseUIElement> any, @Nonnull String text) {
        BaseUIElement uiEl = findUIElement(null, any, IMPLICIT_WAIT);
        sendKeys(uiEl, text);
    }

    public void sendKeys(@Nonnull BaseUIElement uiEl, @Nonnull String text) {
        sendKeys(uiEl, text, true);
    }

    public void sendKeys(@Nonnull BaseUIElement uiEl, @Nonnull String text, boolean hideKeyboard) {
        log.msg(String.format("Type %s in %s", PURPLE.format(text), uiEl.purpleDescr));
        WebElement field = findElement(uiEl);
        field.clear();
        field.sendKeys(text);

        // fast typing sometimes drops characters, so retype up to twice
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String actual = field.getText();
                if (actual == null || actual.length() == text.length()) {
                    break;
                }
                log.msg(String.format("Text mismatch (attempt %d): expected %s, got %s. Retrying...",
                        attempt + 1, PURPLE.format(text), PURPLE.format(String.valueOf(actual))));
                field.clear();
                field.sendKeys(text);
            } catch (Exception e) {
                log.msg("Could not verify text input: " + e.getMessage());
                break;
            }
        }

        if (hideKeyboard) {
            hideKeyboardIfShown();
        }
    }

    public @Nonnull String getAttribute(@Nonnull By by, @Nonnull String attrName) {
        return getAttribute(null, by, attrName);
    }

    public @Nonnull String getAttribute(
            @Nullable WebElement parent, @Nonnull By child, @Nonnull String attrName) {
        log.trace("Find attribute value of " + PURPLE.format(attrName) + " " + child);
        return findElement(parent, child).getAttribute(attrName);
    }

    public boolean isEnabled(@Nonnull By by) {
        return isEnabled(null, by);
    }

    public boolean isEnabled(@Nullable WebElement parent, @Nonnull By by) {
        log.trace("Is element ENABLED " + by);
        return findElement(parent, by).isEnabled();
    }

    public boolean isSelected(@Nonnull By by) {
        return isSelected(null, by);
    }

    public boolean isSelected(@Nullable WebElement parent, @Nonnull By by) {
        log.trace("Is element selected " + by);
        return findElement(parent, by).isSelected();
    }

    public boolean isChecked(@Nonnull By by) {
        return isChecked(null, by);
    }

    public boolean isChecked(@Nullable WebElement parent, @Nonnull By by) {
        return getAttribute(parent, by, CHECKED).equals(TRUE);
    }

    public void drag(int startX, int startY, int endX, int endY) {
        drag(startX, startY, endX, endY, 700);
    }

    public void drag(int startX, int startY, int endX, int endY, int duration) {
        log.debug(String.format("[drag] Drag from position: (%d, %d) to position: (%d, %d)", startX, startY, endX, endY));
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence dragNDrop = new Sequence(finger, 1);
        dragNDrop.addAction(finger.createPointerMove(
                Duration.ofMillis(0), PointerInput.Origin.viewport(), startX, startY));
        dragNDrop.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        dragNDrop.addAction(finger.createPointerMove(
                Duration.ofMillis(duration), PointerInput.Origin.viewport(), endX, endY));
        dragNDrop.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver.perform(Collections.singletonList(dragNDrop));
        // let the scroll animation settle; with 300 ms the next action sometimes started too early
        sleep(500);
    }

    public void pullToRefresh() {
        log.msg("Pull to refresh");
        Dimension size = getScreenSize();
        int startY = (int) (size.height * 0.2);
        int endY = (int) (size.height * 0.8);
        int x = size.width / 2;

        drag(x, startY, x, endY);
    }

    public void pullToRefresh(@Nonnull By elementToPull) {
        log.msg("Pull to refresh");
        Dimension size = getScreenSize();
        int startY = findElement(elementToPull).getRect().getY();
        int endY = size.height;
        int x = size.width / 2;

        drag(x, startY, x, endY);
    }

    public void swipeUp() {
        swipe(UP);
    }

    public void swipeDown() {
        swipe(DOWN);
    }

    public void swipeSmall(@Nonnull Direction direction) {
        swipe(direction, null, 0.35);
    }

    public void swipeLeft() {
        swipe(LEFT);
    }

    public void swipeRight() {
        swipe(RIGHT);
    }

    public void swipe(@Nonnull Direction direction, int times) {
        for (int i = 0; i < times; i++) {
            swipe(direction);
        }
    }

    public void swipe(Direction direction) {
        swipe(direction, null);
    }

    public void swipe(
            @Nonnull Direction direction,
            @Nullable BaseUIElement container) {
        swipe(direction, container, 0.2);
    }

    public void swipe(
            @Nonnull Direction direction,
            @Nullable BaseUIElement container,
            double fraction) {

        Rectangle rect = container == null ?
                new Rectangle(new Point(0, 0), getScreenSize()) :
                findElement(container).getRect();

        String logMsg = container == null ?
                String.format("Swipe %s the screen content with fraction %.2f", direction, fraction) :
                String.format("Swipe %s the content of element %s with fraction %.2f", direction, container.purpleDescr, fraction);

        log.msg(logMsg);
        doSwipe(direction, rect, fraction);
    }

    public void swipe(
            @Nonnull Direction direction,
            @Nonnull WebElement container,
            @Nullable String containerDescr) {
        log.msg(String.format("Swipe %s the content of the element %s", direction, PURPLE.format(containerDescr)));
        Rectangle rect = container.getRect();
        doSwipe(direction, rect);
    }

    public void swipeUpUntilElementVisible(BaseUIElement element, int maxTries) {
        swipeUntilElementVisible(UP, element, maxTries);
    }

    public void swipeUntilElementVisible(
            @Nonnull Direction direction,
            @Nonnull BaseUIElement element,
            int maxTries) {
        swipeUntilElementVisible(direction, null, element, maxTries);
    }

    public void swipeUntilElementVisible(
            @Nonnull Direction direction,
            @Nullable BaseUIElement container,
            @Nonnull BaseUIElement element,
            int maxTries) {
        swipeUntilElementVisible(direction, container, element, maxTries, 0.2);
    }

    /**
     * Swipes the container (or the screen) until the element is fully inside it.
     *
     * @param fraction swipe length while the element is not found (see doSwipe); once it is partly
     *                 visible, shorter swipes are used so it doesn't scroll past
     * @throws NoSuchElementException if the element is not fully visible after maxTries swipes
     */
    public void swipeUntilElementVisible(
            @Nonnull Direction direction,
            @Nullable BaseUIElement container,
            @Nonnull BaseUIElement element,
            int maxTries,
            double fraction) {
        Rectangle contRect = container == null
                ? new Rectangle(new Point(0, 0), getScreenSize())
                : findElement(container).getRect();
        String where = container == null ? "the screen content" : "the content of element " + container.purpleDescr;
        log.startBlock(String.format("Swipe %s %s until element %s gets visible", direction, where, element.purpleDescr));

        while (maxTries-- > 0) {
            List<WebElement> found = findElements(element);
            if (found.isEmpty()) {
                doSwipe(direction, contRect, fraction);
            } else if (isRectangleInside(contRect, found.get(0).getRect())) {
                log.endBlock();
                return;
            } else {
                doSwipe(direction, contRect, Math.min(fraction + 0.2, 0.45));
            }
        }

        throw new NoSuchElementException("The element was not found: " + element.description);
    }

    public void swipeList(Direction direction, List<? extends BaseAbstractScreen> visibleElements) {
        WebElement firstElement = visibleElements.get(0).parent;
        WebElement lastElement = visibleElements.get(visibleElements.size() - 1).parent;

        if (firstElement == null || lastElement == null) {
            throw new InvalidArgumentException("Elements in the list have no parent element");
        }

        switch (direction) {
            case UP:
            case LEFT:
                swipeOver(lastElement, firstElement);
                break;
            case DOWN:
            case RIGHT:
                swipeOver(firstElement, lastElement);
                break;
            default:
                throw new InvalidArgumentException("Direction not supported: " + direction);
        }
    }

    public void swipeOver(WebElement element, WebElement targetElement) {
        Rectangle targetEl = targetElement.getRect();
        int endX = targetEl.getX();
        int endY = targetEl.getY();

        swipeOver(element, endX, endY);
    }

    // Drags the element to (endX, endY) in two moves - a long one, then a short correction - and
    // drags back if it overshoots. Drags shorter than DRAG_SAFETY are skipped: they register as a tap.
    protected void swipeOver(WebElement element, int endX, int endY) {
        final int TARGET_OFFSET = 100;
        final int DRAG_SAFETY = 30;
        final int INTERMEDIATE_DIST = 300;

        int origEndX = endX;
        int origEndY = endY;

        Rectangle navEl = element.getRect();
        int startX = navEl.getX();
        int startY = navEl.getY();

        if (startX == endX && startY == endY) {
            return;
        }

        boolean isVertical = (startX == endX);
        boolean isEndCoordinateSmaller = (endY < startY) || (endX < startX);

        int targetOffset = isEndCoordinateSmaller ? TARGET_OFFSET : -TARGET_OFFSET;
        int intermDist = isEndCoordinateSmaller ? INTERMEDIATE_DIST : -INTERMEDIATE_DIST;

        if (isVertical) {
            endY += targetOffset;
        } else {
            endX += targetOffset;
        }

        log.debug(String.format(
                "[swipeOver Start]: Swipe element with coordinates (%s, %s) on coordinates (%s, %s), height: %s, width: %s",
                startX, startY, endX, endY, navEl.getHeight(), navEl.getWidth()));

        if ((Math.abs(endX - startX) > INTERMEDIATE_DIST + DRAG_SAFETY || Math.abs(endY - startY) > INTERMEDIATE_DIST + DRAG_SAFETY)) {
            int intermediateX = isVertical ? endX : endX + intermDist;
            int intermediateY = isVertical ? endY + intermDist : endY;

            drag(startX, startY, intermediateX, intermediateY);

            try {
                navEl = checkElementNewCoordinates(element, origEndX, origEndY, isVertical, isEndCoordinateSmaller);
            } catch (InvalidElementStateException e) {
                return;
            }
        }

        startX = navEl.getX();
        startY = navEl.getY();
        if (Math.abs(endX - startX) >= DRAG_SAFETY || Math.abs(endY - startY) >= DRAG_SAFETY) {
            drag(startX, startY, endX, endY);

            try {
                checkElementNewCoordinates(element, origEndX, origEndY, isVertical, isEndCoordinateSmaller);
            } catch (InvalidElementStateException e) {
                // already dragged back; nothing more to correct
            }
        }
    }

    // Throws InvalidElementStateException (after dragging back) when the element overshot or left the screen.
    private Rectangle checkElementNewCoordinates(WebElement element, int endX, int endY, boolean isVertical, boolean isEndCoordinateSmaller) {
        final int BACK_DRAG = 100;

        int backDragDist = isEndCoordinateSmaller ? BACK_DRAG : -BACK_DRAG;

        try {
            Rectangle navEl = element.getRect();

            log.debug(String.format(
                    "[swipeOver]: New element coordinates (%s, %s), height: %s, width: %s",
                    navEl.getX(), navEl.getY(), navEl.getHeight(), navEl.getWidth()));

            int elX = navEl.getX();
            int elY = navEl.getY();

            if ((isEndCoordinateSmaller && (elX < endX || elY < endY)) ||
                    (!isEndCoordinateSmaller && (elX > endX || elY > endY))) {
                throw new InvalidElementStateException("The element has passed by the target position");
            }

            // Appium reports a negative height for an element that is only partly on screen
            if (navEl.getHeight() < 0 || navEl.getWidth() < 0) {
                throw new InvalidElementStateException("The height/width of the element is a negative number");
            }

            return navEl;
        } catch (StaleElementReferenceException | InvalidElementStateException e) {
            log.debug("The element is out of the screen, swiping back.");
            int adjustedEndX = isVertical ? endX : endX + backDragDist;
            int adjustedEndY = isVertical ? endY + backDragDist : endY;
            drag(endX, endY, adjustedEndX, adjustedEndY);

            throw new InvalidElementStateException();
        }
    }

    private void doSwipe(
            @Nonnull Direction direction,
            @Nonnull Rectangle container) {
        doSwipe(direction, container, 0.2);
    }

    // fraction (0..0.5) is the margin left at both ends of the container: 0.2 is a long swipe, 0.4 a short one
    private void doSwipe(
            @Nonnull Direction direction,
            @Nonnull Rectangle container,
            double fraction) {
        if (fraction <= 0 || fraction >= 0.5) {
            throw new InvalidArgumentException("Fraction should be between 0.1 and 0.5");
        }

        int startX = (int) (container.x + container.width * 0.5);
        int startY = (int) (container.y + container.height * 0.5);
        int endX = (int) (container.x + container.width * 0.5);
        int endY = (int) (container.y + container.height * 0.5);

        switch (direction) {
            case UP:
                startY = (int) (container.y + container.height * (1.0 - fraction));
                endY = (int) (container.y + container.height * fraction);
                break;
            case DOWN:
                startY = (int) (container.y + container.height * fraction);
                endY = (int) (container.y + container.height * (1.0 - fraction));
                break;
            case LEFT:
                startX = (int) (container.x + container.width * (1.0 - fraction));
                endX = (int) (container.x + container.width * fraction);
                break;
            case RIGHT:
                startX = (int) (container.x + container.width * fraction);
                endX = (int) (container.x + container.width * (1.0 - fraction));
                break;
            default:
                throw new InvalidArgumentException("Direction not supported: " + direction);
        }

        drag(startX, startY, endX, endY);
    }

    private boolean isRectangleInside(Rectangle container, Rectangle innerElement) {
        int innerRight = innerElement.x + innerElement.width;
        int innerBottom = innerElement.y + innerElement.height;

        return innerElement.x >= container.x
                && innerElement.y >= container.y
                && innerRight <= container.x + container.width
                && innerBottom <= container.y + container.height;
    }

    public void sleep(long millis) {
        if (millis >= 1000) {
            log.debug("Sleep for " + millis + " millis");
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public @Nonnull String getPageSource() {
        return driver.getPageSource();
    }

    public @Nonnull Dimension getScreenSize() {
        return driver.manage().window().getSize();
    }

    public void quit() {
        log.msg("Close driver");
        driver.quit();
    }

    // Calls the attempt every INTERVAL_DELAY ms until it returns non-null or the timeout passes.
    // The implicit wait is off meanwhile, so the timeout is the total wait.
    private @Nullable <T> T pollFor(@Nonnull Supplier<T> attempt, int timeOutInSeconds) {
        return withImplicitWait(0, () -> {
            Instant endTime = CLOCK.instant().plusSeconds(timeOutInSeconds);
            while (true) {
                T result = attemptOrNullIfStale(attempt);
                if (result != null) {
                    return result;
                }
                if (!CLOCK.instant().isBefore(endTime)) {
                    return null;
                }
                sleep(INTERVAL_DELAY);
            }
        });
    }

    /**
     * Runs the action with the given implicit wait, then restores the previous one (also when calls
     * nest). If the action fails and the restore fails too, the action's exception is the one thrown.
     */
    protected <T> T withImplicitWait(int seconds, @Nonnull Supplier<T> action) {
        int previous = implicitWait;
        setImplicitWait(seconds);
        Throwable failure = null;
        try {
            return action.get();
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            try {
                setImplicitWait(previous);
            } catch (RuntimeException restoreFailure) {
                if (failure == null) {
                    throw restoreFailure;
                }
                failure.addSuppressed(restoreFailure);
            }
        }
    }

    // The screen re-rendered between finding an element and reading it: try again on the next pass.
    private static @Nullable <T> T attemptOrNullIfStale(@Nonnull Supplier<T> attempt) {
        try {
            return attempt.get();
        } catch (StaleElementReferenceException e) {
            return null;
        }
    }

    private @Nullable WebElement findFirst(@Nullable WebElement parent, @Nonnull List<By> any) {
        List<WebElement> elements = firstNonEmptyResult(parent, any);
        return elements == null ? null : elements.get(0);
    }

    private @Nullable List<WebElement> firstNonEmptyResult(@Nullable WebElement parent, @Nonnull List<By> any) {
        for (By by : any) {
            List<WebElement> elements = findElements(parent, by);
            if (!elements.isEmpty()) {
                return elements;
            }
        }
        return null;
    }

    private @Nullable By firstMatchingBy(@Nonnull List<By> any) {
        for (By by : any) {
            if (!findElements(null, by).isEmpty()) {
                return by;
            }
        }
        return null;
    }

    private @Nullable BaseUIElement firstMatchingUIElement(
            @Nullable WebElement parent, @Nonnull List<? extends BaseUIElement> any) {
        for (BaseUIElement element : any) {
            if (!findElements(parent, element.by).isEmpty()) {
                return element;
            }
        }
        return null;
    }

    private static @Nullable List<WebElement> nonEmptyOrNull(@Nonnull List<WebElement> elements) {
        return elements.isEmpty() ? null : elements;
    }

    private static @Nullable WebElement displayedOrNull(@Nullable WebElement element) {
        return element != null && element.isDisplayed() ? element : null;
    }

    private void setImplicitWait(int seconds) {
        implicitWait = seconds;
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(seconds));
    }

    private List<By> toBys(List<? extends BaseUIElement> elements) {
        return elements.stream()
                .map(el -> el.by)
                .collect(Collectors.toList());
    }
}