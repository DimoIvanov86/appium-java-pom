package tests.android;

import core.automation.ADB;
import core.automation.DriverWrapper;
import core.automation.selfhealing.LocatorHealer;
import core.utilities.StackTraceParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Properties;

import static core.automation.ADB.LOG_DATE_FORMAT;
import static core.utilities.RandomValuesGenerator.getDateTime;

/**
 * Loads the config once per suite, logs each test's start and end, saves its logcat and provides
 * the cleanup. Credentials belong in environment variables, not in the config file.
 */
public class BaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(BaseTest.class);

    protected static @Nullable String androidConfig;

    private String shortTestName;
    private String testStartTimestamp;
    private String device;
    private String methodName;

    @Parameters({"config", "selfHealing"})
    @BeforeSuite(alwaysRun = true)
    public void setUpTestRun(@Nonnull String config, @Optional @Nullable String selfHealingParam) throws IOException {
        androidConfig = config;
        Properties properties = new Properties();
        try (FileInputStream in = new FileInputStream(config)) {
            properties.load(in);
        }
        // -Ppom.selfHealing overrides the config file
        String selfHealing = selfHealingParam != null ? selfHealingParam : properties.getProperty("selfHealing");
        LocatorHealer.initIfEnabled(Boolean.parseBoolean(selfHealing));
    }

    @Parameters({"deviceName"})
    @BeforeMethod(alwaysRun = true)
    public void initTestContext(@Nonnull String deviceName, @Nonnull Method method) {
        device = deviceName;
        methodName = method.getName();
        shortTestName = method.getDeclaringClass().getSimpleName() + "." + methodName;
        testStartTimestamp = getDateTime(LOG_DATE_FORMAT);

        LOG.info("[{}:{}:START]", device, methodName);
    }

    @Parameters("udid")
    @AfterMethod(alwaysRun = true)
    protected void processResult(@Nonnull String udid, @Nonnull ITestResult result) {
        Throwable throwable = result.getThrowable();
        if (throwable != null) {
            String excp = StackTraceParser.frameworkStackTrace(throwable.getStackTrace(), false);
            if (excp.isEmpty()) {
                excp = StackTraceParser.stackTrace(throwable.getStackTrace(), false);
            }
            LOG.info("[{}:{}:FINISHED:EXCEPTION]\n{}", device, methodName, excp);
        } else {
            LOG.info("[{}:{}:FINISHED]", device, methodName);
        }

        // save phone logs no matter if the test is failed or passed
        ADB.logcat(shortTestName, udid, testStartTimestamp, getDateTime(LOG_DATE_FORMAT));

        LocatorHealer.generateReport();
    }

    protected void cleanupAndQuit(DriverWrapper driverWrapper) {
        driverWrapper.log.cleanUpSection();
        // runs in the test's finally: anything thrown here would hide the test's own result, so it is only logged
        try {
            driverWrapper.terminateApp(driverWrapper.appId);
        } catch (Throwable e) {
            driverWrapper.log.error("Could not close the app", e);
        } finally {
            quitSession(driverWrapper);
        }
        driverWrapper.log.endCleanupSection();
    }

    private void quitSession(DriverWrapper driverWrapper) {
        try {
            driverWrapper.quit();
        } catch (Throwable e) {
            driverWrapper.log.error("Could not end the Appium session", e);
        }
    }
}
