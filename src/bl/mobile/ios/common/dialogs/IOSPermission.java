package bl.mobile.ios.common.dialogs;

import bl.mobile.ios.common.base.IOSAbstractScreen;
import core.automation.IOSDriverWrapper;

import javax.annotation.Nonnull;

import static core.automation.AbstractDriverWrapper.IMPLICIT_WAIT;
import static core.reports.HtmlFormatter.Color.PURPLE;

/**
 * An iOS permission alert, recognised by a fragment of its text: the full text contains the app
 * name and its wording differs between iOS versions.
 */
public class IOSPermission extends IOSAbstractScreen {
    public enum Type {
        CAMERA("access the camera"),
        MICROPHONE("access the microphone"),
        LOCATION("use your location"),
        NOTIFICATIONS("send you notifications");

        public final String alertTextFragment;

        Type(String alertTextFragment) {
            this.alertTextFragment = alertTextFragment;
        }
    }

    // the curly apostrophe is what iOS renders in this button's label
    public static final String DONT_ALLOW = "Don’t Allow";

    private final @Nonnull Type type;

    public IOSPermission(
            @Nonnull Type type,
            @Nonnull IOSDriverWrapper driverWrapper) {
        super(driverWrapper);

        this.type = type;
    }

    public void acceptIfPresent() {
        if (!isDisplayed()) {
            log.debug("No " + type + " permission alert");
            return;
        }
        log.msg("Accept the " + PURPLE.format(type.toString()) + " permission alert");
        driverWrapper.acceptAlert();
    }

    public void clickIfPresent(@Nonnull String buttonLabel) {
        if (!isDisplayed()) {
            log.debug("No " + type + " permission alert");
            return;
        }
        log.msg("Click " + PURPLE.format(buttonLabel) + " on the " + type + " permission alert");
        driverWrapper.clickAlertButton(buttonLabel);
    }

    @Override
    public void verify() {
        log.verify(PURPLE.format(type.toString()) + " permission alert is displayed", isDisplayed());
    }

    @Override
    public void assertDisplayed() {
        assertDisplayed(PURPLE.format(type.toString()) + " permission alert has been displayed");
    }

    @Override
    public boolean isDisplayed() {
        String alertText = driverWrapper.getAlertText(IMPLICIT_WAIT);
        return alertText != null && alertText.toLowerCase().contains(type.alertTextFragment);
    }
}
