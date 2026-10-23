package tests.ios;

import core.automation.IOSDriverWrapper;
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

/**
 * Loads the config once per suite, logs each test's start and end and provides the cleanup.
 * Credentials belong in environment variables, not in the config file.
 */
public class IOSBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(IOSBaseTest.class);

    protected static @Nullable String iosConfig;

    private String device;
    private String methodName;

    @Parameters({"config", "selfHealing"})
    @BeforeSuite(alwaysRun = true)
    public void setUpTestRun(@Nonnull String config, @Optional @Nullable String selfHealingParam) throws IOException {
        iosConfig = config;
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

        LOG.info("[{}:{}:START]", device, methodName);
    }

    @AfterMethod(alwaysRun = true)
    protected void processResult(@Nonnull ITestResult result) {
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

        LocatorHealer.generateReport();
    }

    protected void cleanupAndQuit(IOSDriverWrapper driverWrapper) {
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

    private void quitSession(IOSDriverWrapper driverWrapper) {
        try {
            driverWrapper.quit();
        } catch (Throwable e) {
            driverWrapper.log.error("Could not end the Appium session", e);
        }
    }
}
