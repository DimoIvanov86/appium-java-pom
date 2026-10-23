package bl.mobile.android.article;

import bl.mobile.android.common.UIElement;
import bl.mobile.android.common.base.AbstractScreen;
import core.automation.DriverWrapper;

import javax.annotation.Nonnull;

import static bl.mobile.android.common.UIElement.byId;
import static bl.mobile.android.common.UIElement.byXpath;
import static core.automation.AbstractDriverWrapper.AVERAGE_WAIT;

/**
 * A Wikipedia article. The body, title included, is a WebView, so the title is a text node inside it.
 */
public class ArticleScreen extends AbstractScreen {

    // Not yet checked on a device; confirm with Appium Inspector.
    private static final UIElement
            CONTENT = byId("org.wikipedia:id/page_web_view", "article content");
    private static final String
            TITLE_XPATH = "//*[@resource-id='org.wikipedia:id/page_web_view']//*[@text='%s']";

    public ArticleScreen(@Nonnull DriverWrapper driverWrapper) {
        super(driverWrapper);
        driverWrapper.waitToAppear(CONTENT, AVERAGE_WAIT);
        assertDisplayed();
    }

    public void verifyTitle(@Nonnull String expectedTitle) {
        UIElement title = byXpath(String.format(TITLE_XPATH, expectedTitle), expectedTitle, "article title");
        verify(title);
    }

    @Override
    public void verify() {
        log.startBlock("Verify the article screen");
        verify(CONTENT);
        log.endBlock();
    }

    @Override
    public void assertDisplayed() {
        assertDisplayed("Article screen has been displayed");
    }

    @Override
    public boolean isDisplayed() {
        return driverWrapper.onScreen(CONTENT);
    }
}
