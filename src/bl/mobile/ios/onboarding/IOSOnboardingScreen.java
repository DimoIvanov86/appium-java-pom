package bl.mobile.ios.onboarding;

import bl.mobile.ios.common.base.IOSAbstractScreen;
import bl.mobile.ios.common.base.IOSUIElement;
import core.automation.IOSDriverWrapper;
import org.openqa.selenium.TimeoutException;

import javax.annotation.Nonnull;

import static bl.mobile.ios.common.base.IOSUIElement.buttonByLabel;
import static core.automation.AbstractDriverWrapper.SHORT_WAIT;

/**
 * The welcome carousel the Wikipedia app shows on the first launch after an install.
 */
public class IOSOnboardingScreen extends IOSAbstractScreen {

    // Not yet checked on a device; confirm with Appium Inspector.
    private static final IOSUIElement
            SKIP_BTN = buttonByLabel("Skip");

    public IOSOnboardingScreen(@Nonnull IOSDriverWrapper driverWrapper) {
        super(driverWrapper);
        assertDisplayed();
    }

    // The onboarding appears only on the first launch after an install, and not in every app version.
    public static void skipIfShown(@Nonnull IOSDriverWrapper driverWrapper) {
        try {
            driverWrapper.waitToAppear(SKIP_BTN, SHORT_WAIT);
        } catch (TimeoutException e) {
            driverWrapper.log.msg("No onboarding on this launch");
            return;
        }
        new IOSOnboardingScreen(driverWrapper).clickSkip();
    }

    public void clickSkip() {
        driverWrapper.click(SKIP_BTN);
    }

    @Override
    public void verify() {
        log.startBlock("Verify the onboarding screen");
        verify(SKIP_BTN);
        log.endBlock();
    }

    @Override
    public void assertDisplayed() {
        assertDisplayed("Onboarding screen has been displayed");
    }

    @Override
    public boolean isDisplayed() {
        return driverWrapper.onScreen(SKIP_BTN);
    }
}
