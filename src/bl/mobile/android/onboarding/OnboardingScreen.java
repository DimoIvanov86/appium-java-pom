package bl.mobile.android.onboarding;

import bl.mobile.android.common.UIElement;
import bl.mobile.android.common.base.AbstractScreen;
import core.automation.DriverWrapper;
import org.openqa.selenium.TimeoutException;

import javax.annotation.Nonnull;

import static bl.mobile.android.common.UIElement.byId;
import static core.automation.AbstractDriverWrapper.SHORT_WAIT;

/**
 * The welcome carousel the Wikipedia app shows on the first launch after an install.
 */
public class OnboardingScreen extends AbstractScreen {

    // Not yet checked on a device; confirm with Appium Inspector.
    private static final UIElement
            SKIP_BTN = byId("org.wikipedia:id/fragment_onboarding_skip_button", "Skip", "'Skip' button");

    public OnboardingScreen(@Nonnull DriverWrapper driverWrapper) {
        super(driverWrapper);
        assertDisplayed();
    }

    // The onboarding appears only on the first launch after an install, and not in every app version.
    public static void skipIfShown(@Nonnull DriverWrapper driverWrapper) {
        try {
            driverWrapper.waitToAppear(SKIP_BTN, SHORT_WAIT);
        } catch (TimeoutException e) {
            driverWrapper.log.msg("No onboarding on this launch");
            return;
        }
        new OnboardingScreen(driverWrapper).clickSkip();
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
