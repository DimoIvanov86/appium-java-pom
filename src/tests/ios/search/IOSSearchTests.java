package tests.ios.search;

import bl.mobile.ios.article.IOSArticleScreen;
import bl.mobile.ios.onboarding.IOSOnboardingScreen;
import bl.mobile.ios.search.IOSSearchScreen;
import core.automation.IOSDriverWrapper;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import tests.ios.IOSBaseTest;

public class IOSSearchTests extends IOSBaseTest {

    private static final String SEARCH_TERM = "Appium";

    @Test
    @Parameters({"udid", "deviceName"})
    public void searchOpensArticle(String udid, String deviceName) {
        IOSDriverWrapper driverWrapper = new IOSDriverWrapper(udid, deviceName, iosConfig);
        driverWrapper.log.startTest("Search for an article and open it from the results");
        try {
            IOSOnboardingScreen.skipIfShown(driverWrapper);
            IOSSearchScreen searchScreen = IOSSearchScreen.open(driverWrapper);

            driverWrapper.log.startTestScenario();
            driverWrapper.log.startBlock("Search for " + SEARCH_TERM);
            searchScreen.searchFor(SEARCH_TERM);
            driverWrapper.log.endBlock();

            driverWrapper.log.startBlock("Open the first result and check its title");
            IOSArticleScreen articleScreen = searchScreen.openFirstResult();
            articleScreen.verifyTitle(SEARCH_TERM);
            driverWrapper.log.endBlock();

            driverWrapper.log.verifyTestStatus();
        } catch (Throwable e) {
            driverWrapper.log.handleException(e);
            throw e;
        } finally {
            cleanupAndQuit(driverWrapper);
        }
    }
}
