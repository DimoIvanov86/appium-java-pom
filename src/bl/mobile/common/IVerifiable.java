package bl.mobile.common;

import javax.annotation.Nonnull;

/**
 * Implemented by screens, dialogs and alerts. Single controls (buttons, labels, fields) are not
 * verifiable on their own; they are UI elements of a verifiable component.
 */
public interface IVerifiable {

    // Soft-checks every element of the component and its text by design.
    void verify();

    // Fails the test if the component is not shown; allows for the time it takes to load.
    void assertDisplayed();

    void assertDisplayed(@Nonnull String logMsg);

    boolean isDisplayed();
}
