package tests.android.search;

import bl.mobile.android.article.ArticleScreen;
import bl.mobile.android.onboarding.OnboardingScreen;
import bl.mobile.android.search.SearchScreen;
import core.automation.DriverWrapper;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import tests.android.BaseTest;

public class SearchTests extends BaseTest {

    private static final String SEARCH_TERM = "Appium";

    @Test
    @Parameters({"udid", "port", "deviceName"})
    public void searchOpensArticle(String udid, String port, String deviceName) {
        DriverWrapper driverWrapper = new DriverWrapper(udid, port, deviceName, androidConfig);
        driverWrapper.log.startTest("Search for an article and open it from the results");
        try {
            OnboardingScreen.skipIfShown(driverWrapper);
            SearchScreen searchScreen = SearchScreen.open(driverWrapper);

            driverWrapper.log.startTestScenario();
            driverWrapper.log.startBlock("Search for " + SEARCH_TERM);
            searchScreen.searchFor(SEARCH_TERM);
            driverWrapper.log.endBlock();

            driverWrapper.log.startBlock("Open the first result and check its title");
            ArticleScreen articleScreen = searchScreen.openFirstResult();
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
