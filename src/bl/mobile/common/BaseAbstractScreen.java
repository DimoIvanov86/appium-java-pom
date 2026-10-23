package bl.mobile.common;

import bl.mobile.common.exceptions.NoSuchScreenException;
import core.automation.AbstractDriverWrapper;
import core.reports.Log;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static core.automation.AbstractDriverWrapper.AVERAGE_WAIT;
import static core.reports.HtmlFormatter.Color.PURPLE;
import static core.reports.HtmlFormatter.NEW_LINE_EMPHASISED;

/**
 * Base of every page object: a full screen, or a dialog, alert or menu shown over one.
 * Provides the verify methods that log each check in a consistent way.
 */
public abstract class BaseAbstractScreen implements IVerifiable {

    public final @Nullable WebElement parent;

    public final @Nonnull Log log;
    private final @Nonnull AbstractDriverWrapper driverWrapper;

    protected BaseAbstractScreen(@Nonnull AbstractDriverWrapper driverWrapper) {
        this(null, driverWrapper);
    }

    protected BaseAbstractScreen(@Nullable WebElement parent, @Nonnull AbstractDriverWrapper driverWrapper) {
        this.log = driverWrapper.log;
        this.driverWrapper = driverWrapper;
        this.parent = parent;
    }

    @Override
    public void assertDisplayed(@Nonnull String logMsg) {
        // isDisplayed only checks presence, so give a screen that is still loading some time
        if (driverWrapper.waitUntil(this::isDisplayed, AVERAGE_WAIT)) {
            log.verify(logMsg, true);
            return;
        }

        log.error(logMsg);
        throw new NoSuchScreenException(logMsg);
    }

    public void verify(@Nonnull BaseUIElement uiEl) {
        verify(null, uiEl);
    }

    // Soft-checks that the element is shown and, if it has a text by design, that the text matches.
    public void verify(@Nullable WebElement parent, @Nonnull BaseUIElement child) {
        try {
            verify(child, driverWrapper.getText(parent, child));
        } catch (NoSuchElementException e) {
            log.verify(String.format("%s not displayed on the screen", child.purpleDescr), e);
        }
    }

    // Like verify, but a missing element passes.
    public void verifyOptional(@Nullable WebElement parent, @Nonnull BaseUIElement child) {
        try {
            verify(child, driverWrapper.getText(parent, child));
        } catch (NoSuchElementException e) {
            log.verify(String.format("The optional element %s is not available on the screen", child.purpleDescr), true);
        }
    }

    public void verifyMissing(@Nonnull BaseUIElement uiEl) {
        verifyMissing(null, uiEl);
    }

    public void verifyMissing(@Nullable WebElement parent, @Nonnull BaseUIElement child) {
        boolean onScreen = driverWrapper.onScreen(parent, child);
        log.verify(String.format("Element %s is not available on the screen", child.purpleDescr), !onScreen);
    }

    public void verifyDisabled(@Nonnull BaseUIElement uiEl) {
        log.verify(uiEl.purpleDescr + " is inactive", !driverWrapper.isEnabled(uiEl.by));
    }

    private void verify(@Nonnull BaseUIElement uiEl, @Nonnull String textOnScreen) {
        String textByDesign = uiEl.textByDesign;
        boolean longText = textByDesign.length() > 120;
        boolean correct = false;
        String msg;
        if (textByDesign.isEmpty()) { // dynamic text: check presence only
            msg = String.format(
                    "%s is displayed%s",
                    uiEl.purpleDescr,
                    textOnScreen.isEmpty() ? "" : ", its value is: " + PURPLE.format(textOnScreen));
            correct = true;
        } else {
            if (textOnScreen.endsWith("…")) { // truncated by the layout: compare the visible part
                String textOnScreenNotEllipsized = StringUtils.substringBeforeLast(textOnScreen, "…");
                correct = textByDesign.startsWith(textOnScreenNotEllipsized);
            } else {
                correct = textOnScreen.equals(textByDesign);
            }
            msg = String.format(
                    "%s is displayed, its value is: %s ",
                    uiEl.purpleDescr,
                    longText ? NEW_LINE_EMPHASISED.format(textOnScreen) : PURPLE.format(textOnScreen));

            if (!correct) {
                msg += "but the expected value is: " + PURPLE.format(textByDesign);
            }
        }
        log.verify(msg, correct);
    }
}
