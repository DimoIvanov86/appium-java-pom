package bl.mobile.android.search;

import bl.mobile.android.article.ArticleScreen;
import bl.mobile.android.common.UIElement;
import bl.mobile.android.common.base.AbstractScreen;
import core.automation.DriverWrapper;

import javax.annotation.Nonnull;

import static bl.mobile.android.common.UIElement.byId;
import static core.automation.AbstractDriverWrapper.AVERAGE_WAIT;
import static core.automation.AbstractDriverWrapper.LONG_WAIT;

/**
 * The Wikipedia search page: a search field with the matching articles listed below it.
 */
public class SearchScreen extends AbstractScreen {

    // Not yet checked on a device; confirm with Appium Inspector.
    private static final UIElement
            SEARCH_BAR = byId("org.wikipedia:id/search_container", "'Search Wikipedia' bar"),
            SEARCH_FIELD = byId("org.wikipedia:id/search_src_text", "search field"),
            RESULT_TITLE = byId("org.wikipedia:id/page_list_item_title", "search result title");

    public SearchScreen(@Nonnull DriverWrapper driverWrapper) {
        super(driverWrapper);
        assertDisplayed();
    }

    // from the search bar at the top of the start screen
    public static SearchScreen open(@Nonnull DriverWrapper driverWrapper) {
        // the start screen loads its feed first
        driverWrapper.waitToAppear(SEARCH_BAR, LONG_WAIT);
        driverWrapper.click(SEARCH_BAR);
        return new SearchScreen(driverWrapper);
    }

    public SearchScreen searchFor(@Nonnull String term) {
        // keep the keyboard: hiding it with BACK can close the search page
        driverWrapper.sendKeys(SEARCH_FIELD, term, false);
        driverWrapper.waitToAppear(RESULT_TITLE, AVERAGE_WAIT);
        return this;
    }

    // findElement returns the first match, which is the top result
    public ArticleScreen openFirstResult() {
        driverWrapper.click(RESULT_TITLE);
        return new ArticleScreen(driverWrapper);
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
