package bl.mobile.ios.search;

import bl.mobile.ios.article.IOSArticleScreen;
import bl.mobile.ios.common.base.IOSAbstractScreen;
import bl.mobile.ios.common.base.IOSUIElement;
import core.automation.IOSDriverWrapper;

import javax.annotation.Nonnull;

import static bl.mobile.ios.common.base.IOSUIElement.byClassChain;
import static core.automation.AbstractDriverWrapper.AVERAGE_WAIT;
import static core.automation.AbstractDriverWrapper.LONG_WAIT;

/**
 * The Wikipedia search page: a search field with the matching articles listed below it.
 */
public class IOSSearchScreen extends IOSAbstractScreen {

    // Not yet checked on a device; confirm with Appium Inspector.
    private static final IOSUIElement
            SEARCH_FIELD = byClassChain(
                    "**/XCUIElementTypeSearchField[`label == \"Search Wikipedia\"`]",
                    "'Search Wikipedia' field"),
            RESULT_CELL = byClassChain(
                    "**/XCUIElementTypeCollectionView/XCUIElementTypeCell",
                    "search result");

    public IOSSearchScreen(@Nonnull IOSDriverWrapper driverWrapper) {
        super(driverWrapper);
        assertDisplayed();
    }

    // from the search field at the top of the start screen
    public static IOSSearchScreen open(@Nonnull IOSDriverWrapper driverWrapper) {
        // the start screen loads its feed first
        driverWrapper.waitToAppear(SEARCH_FIELD, LONG_WAIT);
        driverWrapper.click(SEARCH_FIELD);
        return new IOSSearchScreen(driverWrapper);
    }

    public IOSSearchScreen searchFor(@Nonnull String term) {
        driverWrapper.sendKeys(SEARCH_FIELD, term, false);
        driverWrapper.waitToAppear(RESULT_CELL, AVERAGE_WAIT);
        return this;
    }

    // findElement returns the first match, which is the top result
    public IOSArticleScreen openFirstResult() {
        driverWrapper.click(RESULT_CELL);
        return new IOSArticleScreen(driverWrapper);
    }

    @Override
    public void verify() {
        log.startBlock("Verify the search screen");
        verify(SEARCH_FIELD);
        log.endBlock();
    }

    @Override
    public void assertDisplayed() {
        assertDisplayed("Search screen has been displayed");
    }

    @Override
    public boolean isDisplayed() {
        return driverWrapper.onScreen(SEARCH_FIELD);
    }
}
