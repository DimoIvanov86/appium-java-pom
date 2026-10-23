package bl.mobile.ios.common.base;

import bl.mobile.common.BaseAbstractScreen;
import bl.mobile.ios.common.dialogs.IOSPermission;
import core.automation.IOSDriverWrapper;
import org.openqa.selenium.WebElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static bl.mobile.ios.common.base.IOSUIElement.byClassChain;
import static bl.mobile.ios.common.dialogs.IOSPermission.DONT_ALLOW;
import static bl.mobile.ios.common.dialogs.IOSPermission.Type.CAMERA;
import static bl.mobile.ios.common.dialogs.IOSPermission.Type.LOCATION;
import static bl.mobile.ios.common.dialogs.IOSPermission.Type.MICROPHONE;
import static bl.mobile.ios.common.dialogs.IOSPermission.Type.NOTIFICATIONS;

/**
 * Base class of iOS screens, alerts and components: the iOS driver, the navigation-bar back
 * button and the system permission alerts.
 */
public abstract class IOSAbstractScreen extends BaseAbstractScreen {

    // UIKit's default back button; screens with a custom one override clickBackButton
    private static final IOSUIElement
            BACK_BTN = byClassChain(
                    "**/XCUIElementTypeNavigationBar/XCUIElementTypeButton[`name == 'Back'`]",
                    "'Back' button");

    public final IOSDriverWrapper driverWrapper;

    protected IOSAbstractScreen(@Nonnull IOSDriverWrapper driverWrapper) {
        this(null, driverWrapper);
    }

    protected IOSAbstractScreen(@Nullable WebElement parent, @Nonnull IOSDriverWrapper driverWrapper) {
        super(parent, driverWrapper);
        this.driverWrapper = driverWrapper;
    }

    public void clickBackButton() {
        driverWrapper.click(BACK_BTN);
    }

    // void, not this: a base-class return type would break fluent chains on the subclass
    public void handleCamera() {
        new IOSPermission(CAMERA, driverWrapper).acceptIfPresent();
    }

    public void handleMic() {
        new IOSPermission(MICROPHONE, driverWrapper).acceptIfPresent();
    }

    public void handleNotifications() {
        new IOSPermission(NOTIFICATIONS, driverWrapper).acceptIfPresent();
    }

    // denied, so no test depends on where the device is
    public void handleLocation() {
        new IOSPermission(LOCATION, driverWrapper).clickIfPresent(DONT_ALLOW);
    }
}
