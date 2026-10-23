package bl.mobile.android.common.base;

import bl.mobile.android.common.UIElement;
import bl.mobile.android.common.dialogs.Permission;
import bl.mobile.common.BaseAbstractScreen;
import core.automation.DriverWrapper;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static bl.mobile.android.common.UIElement.byContentDescr;

/**
 * Base class of Android screens, dialogs and components: the Android driver, the toolbar back
 * button and the system permission dialogs.
 */
public abstract class AbstractScreen extends BaseAbstractScreen {

    // AppCompat's content description for the toolbar's up arrow
    protected static final UIElement
            BACK_BTN = byContentDescr("Navigate up", "", "'Back' button");

    public final DriverWrapper driverWrapper;

    protected AbstractScreen(@Nonnull DriverWrapper driverWrapper) {
        this(null, driverWrapper);
    }

    protected AbstractScreen(@Nullable WebElement parent, @Nonnull DriverWrapper driverWrapper) {
        super(parent, driverWrapper);
        this.driverWrapper = driverWrapper;
    }

    public void clickBackButton() {
        driverWrapper.click(BACK_BTN);
    }

    // void, not this: a base-class return type would break fluent chains on the subclass
    public void handleCamera() {
        allowPermissionIfShown("camera");
    }

    public void handleMic() {
        allowPermissionIfShown("microphone");
    }

    public void handleNotifications() {
        allowPermissionIfShown("notifications");
    }

    // denied, so no test depends on where the device is
    public void handleLocation() {
        try {
            new Permission("location", driverWrapper).clickDeny();
        } catch (NoSuchElementException e) {
            log.debug("No location permission dialog");
        }
    }

    // On a fresh install the dialog may or may not be shown; both are fine.
    private void allowPermissionIfShown(@Nonnull String permission) {
        try {
            new Permission(permission, driverWrapper).clickAllow();
        } catch (NoSuchElementException e) {
            log.debug("No " + permission + " permission dialog");
        }
    }
}
