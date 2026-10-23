package bl.mobile.ios.article;

import bl.mobile.ios.common.base.IOSAbstractScreen;
import bl.mobile.ios.common.base.IOSUIElement;
import core.automation.IOSDriverWrapper;

import javax.annotation.Nonnull;

import static bl.mobile.ios.common.base.IOSUIElement.byClassChain;
import static core.automation.AbstractDriverWrapper.AVERAGE_WAIT;

/**
 * A Wikipedia article. The body, title included, is a web view, so the title is a static text inside it.
 */
public class IOSArticleScreen extends IOSAbstractScreen {

    // Not yet checked on a device; confirm with Appium Inspector.
    private static final IOSUIElement
            CONTENT = byClassChain("**/XCUIElementTypeWebView", "article content");
    private static final String
            TITLE_CLASS_CHAIN = "**/XCUIElementTypeWebView/**/XCUIElementTypeStaticText[`label == \"%s\"`]";

    public IOSArticleScreen(@Nonnull IOSDriverWrapper driverWrapper) {
        super(driverWrapper);
        driverWrapper.waitToAppear(CONTENT, AVERAGE_WAIT);
        assertDisplayed();
    }

    public void verifyTitle(@Nonnull String expectedTitle) {
        IOSUIElement title = byClassChain(String.format(TITLE_CLASS_CHAIN, expectedTitle), "article title");
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
