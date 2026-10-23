package core.automation;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static core.automation.AbstractDriverWrapper.Platform;
import static tools.config.Config.ADB_PORT_DEFAULT;
import static tools.config.Config.ADB_PORT_PROPERTY;
import static tools.config.Config.APPIUM_PORT_DEFAULT;
import static tools.config.Config.APPIUM_PORT_PROPERTY;
import static tools.config.Config.WDA_CONNECTION_TIMEOUT_DEFAULT;
import static tools.config.Config.WDA_CONNECTION_TIMEOUT_PROPERTY;
import static tools.config.Config.XCODE_ORG_ID_PROPERTY;
import static tools.config.Config.XCODE_SIGNING_ID_PROPERTY;

/**
 * Starts Appium sessions from the config file: capabilities, and on iOS a clean install of the app.
 */
public class Drivers {

    // the step log (Log) needs a driver, so session start-up is logged here
    private static final Logger LOG = LoggerFactory.getLogger(Drivers.class);

    private final Properties config;
    private final URL appiumUrl;

    public Drivers(String appConfig) {
        config = new Properties();
        try (FileInputStream in = new FileInputStream(appConfig)) {
            config.load(in);
            // urlHost has a %s for the port, so one config serves several Appium servers
            String appiumPort = System.getProperty(APPIUM_PORT_PROPERTY, APPIUM_PORT_DEFAULT);
            appiumUrl = new URL(String.format(config.getProperty("urlHost"), appiumPort));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the config file " + appConfig, e);
        }
    }

    /**
     * @return appPackage (Android) or iosBundleId (iOS) from the config
     * @throws IllegalStateException if the key is missing or empty
     */
    public @Nonnull String getAppId(@Nonnull Platform platform) {
        String key = platform == Platform.ANDROID ? "appPackage" : "iosBundleId";
        String appId = config.getProperty(key, "");
        if (appId.isEmpty()) {
            throw new IllegalStateException("'" + key + "' is not set in the config file");
        }
        return appId;
    }

    public AndroidDriver getAndroidDriver(
            @Nonnull String udid,
            @Nonnull String systemPort,
            @Nonnull String deviceName,
            boolean reset) {
        LOG.info("Connecting to Appium at {} for device {}", appiumUrl, udid);
        // a forward left by a crashed session blocks the new one
        new ADB(udid).clearUIAutomatorPort(Integer.parseInt(systemPort));
        return new AndroidDriver(appiumUrl, androidCapabilities(udid, systemPort, deviceName, reset));
    }

    public IOSDriver getIOSDriver(@Nonnull String udid, @Nonnull String deviceName) {
        LOG.info("Connecting to Appium at {} for device {}", appiumUrl, udid);
        IOSDriver driver = new IOSDriver(appiumUrl, iosCapabilities(udid, deviceName));
        try {
            startCleanApp(driver);
        } catch (RuntimeException e) {
            AbstractDriverWrapper.quitAfterFailedStart(driver, e);
            throw e;
        }
        return driver;
    }

    private DesiredCapabilities androidCapabilities(
            String udid, String systemPort, String deviceName, boolean reset) {
        DesiredCapabilities cap = new DesiredCapabilities();
        cap.setCapability("platformName", "Android");
        cap.setCapability("appium:automationName", "UiAutomator2");
        cap.setCapability("appium:systemPort", Integer.parseInt(systemPort));
        cap.setCapability("appium:udid", udid);
        cap.setCapability("appium:deviceName", deviceName);
        cap.setCapability("appium:appPackage", getAppId(Platform.ANDROID));
        cap.setCapability("appium:appActivity", config.getProperty("appActivity"));

        String adbPort = System.getProperty(ADB_PORT_PROPERTY, ADB_PORT_DEFAULT);
        cap.setCapability("appium:adbPort", Integer.parseInt(adbPort));
        cap.setCapability("appium:adbExecTimeout", 60000);
        cap.setCapability("appium:androidInstallTimeout", 180000);
        cap.setCapability("appium:uiautomator2ServerInstallTimeout", 90000);
        cap.setCapability("appium:deviceReadyTimeout", 60);

        // noReset=false clears the app data, so each test starts from a fresh state
        cap.setCapability("appium:noReset", !reset);
        cap.setCapability("appium:newCommandTimeout", 5 * 60);
        cap.setCapability("appium:printPageSourceOnFindFailure", false);
        cap.setCapability("appium:shouldTerminateApp", true);
        cap.setCapability("appium:waitForIdleTimeout", 400);
        cap.setCapability("appium:forceAppLaunch", true);

        // otherwise bottom sheets and other secondary windows are invisible
        Map<String, Object> appiumSettings = new HashMap<>();
        appiumSettings.put("enableMultiWindows", true);
        cap.setCapability("appium:settings", appiumSettings);
        return cap;
    }

    private DesiredCapabilities iosCapabilities(String udid, String deviceName) {
        DesiredCapabilities cap = new DesiredCapabilities();
        cap.setCapability("platformName", "iOS");
        cap.setCapability("appium:automationName", "XCUITest");
        cap.setCapability("appium:udid", udid);
        cap.setCapability("appium:deviceName", deviceName);
        cap.setCapability("appium:newCommandTimeout", 5 * 60);

        // derived from the udid so parallel devices get different ports
        int wdaPort = 8100 + Math.abs(udid.hashCode() % 100);
        cap.setCapability("appium:wdaLocalPort", wdaPort);
        LOG.info("Using WDA port {} for device {}", wdaPort, udid);

        cap.setCapability("appium:wdaLaunchTimeout", 180000);
        // lower it (-Ppom.wdaConnectionTimeout=30000) to fail fast while debugging hangs
        String wdaConnectionTimeout = System.getProperty(WDA_CONNECTION_TIMEOUT_PROPERTY, WDA_CONNECTION_TIMEOUT_DEFAULT);
        cap.setCapability("appium:wdaConnectionTimeout", Integer.parseInt(wdaConnectionTimeout));
        cap.setCapability("appium:wdaStartupRetries", 4);
        cap.setCapability("appium:wdaStartupRetryInterval", 15000);
        addWdaSigning(cap, udid);

        // iOS 26+ animations never report idle
        if (readIosMajor(udid) >= 26) {
            cap.setCapability("appium:waitForIdleTimeout", 0);
        }

        cap.setCapability("appium:showXcodeLog", true);
        cap.setCapability("appium:printPageSourceOnFindFailure", false);
        cap.setCapability("appium:shouldTerminateApp", true);
        cap.setCapability("appium:forceAppLaunch", true);
        cap.setCapability("appium:reduceMotion", true);
        cap.setCapability("appium:simpleIsVisibleCheck", true);
        cap.setCapability("appium:screenshotQuality", 2);
        cap.setCapability("appium:disableAutomaticScreenshots", true);

        // limit the accessibility snapshot so heavy screens don't time out
        Map<String, Object> iosSettings = new HashMap<>();
        iosSettings.put("snapshotMaxDepth", 35);
        iosSettings.put("snapshotMaxChildrenCount", 100);
        iosSettings.put("customSnapshotTimeout", 5);
        cap.setCapability("appium:settings", iosSettings);
        return cap;
    }

    // Real devices need WebDriverAgent signed with your team; simulators need none of this.
    private void addWdaSigning(DesiredCapabilities cap, String udid) {
        String wdaBundleId = config.getProperty("iosWdaBundleId", "");
        if (!wdaBundleId.isEmpty()) {
            cap.setCapability("appium:updatedWDABundleId", wdaBundleId);
            if (isWdaInstalledOnDevice(udid, wdaBundleId)) {
                cap.setCapability("appium:usePrebuiltWDA", true);
            }
        }
        String orgId = System.getProperty(XCODE_ORG_ID_PROPERTY, "");
        if (!orgId.isEmpty()) {
            cap.setCapability("appium:xcodeOrgId", orgId);
        }
        String signingId = System.getProperty(XCODE_SIGNING_ID_PROPERTY, "");
        if (!signingId.isEmpty()) {
            cap.setCapability("appium:xcodeSigningId", signingId);
        }
    }

    // Reinstalling is the only way to clear an iOS app's data, so an iosAppPath means a clean start.
    private void startCleanApp(IOSDriver driver) {
        String bundleId = getAppId(Platform.IOS);
        String appPath = config.getProperty("iosAppPath", "");
        if (!appPath.isEmpty()) {
            if (driver.isAppInstalled(bundleId)) {
                LOG.info("Removing the installed {} for a clean start", bundleId);
                driver.removeApp(bundleId);
            }
            driver.installApp(appPath);
        }
        activateAppWithRetry(driver, bundleId);
    }

    private static int readIosMajor(String udid) {
        try {
            Process p = new ProcessBuilder("xcrun", "xctrace", "list", "devices")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            // e.g. "My iPhone (26.4.2) (00008110-000A1B2C3D4E5F60)"
            Pattern versionBeforeUdid = Pattern.compile(
                    "\\((\\d+)\\.\\d+(?:\\.\\d+)?\\)\\s*\\(" + Pattern.quote(udid) + "\\)");
            for (String line : out.split("\n")) {
                Matcher m = versionBeforeUdid.matcher(line);
                if (m.find()) {
                    return Integer.parseInt(m.group(1));
                }
            }
        } catch (IOException e) {
            LOG.warn("Could not read the iOS version of {}; keeping the default capabilities", udid, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return 0;
    }

    private static boolean isWdaInstalledOnDevice(String udid, String wdaBundleId) {
        try {
            Process p = new ProcessBuilder(
                    "xcrun", "devicectl", "device", "info", "apps",
                    "--device", udid).redirectErrorStream(true).start();
            byte[] out = p.getInputStream().readAllBytes();
            p.waitFor();
            return new String(out).contains(wdaBundleId + ".xctrunner");
        } catch (IOException e) {
            // if unsure, let Appium build WDA rather than fail the session
            LOG.warn("Could not list the apps on {}; WebDriverAgent will be built", udid, e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // Right after installApp the system may not know the app yet ("unknown to FrontBoard").
    private static void activateAppWithRetry(IOSDriver driver, String bundleId) {
        int attempts = 4;
        long backoffMs = 1500;
        RuntimeException last = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                driver.activateApp(bundleId);
                return;
            } catch (RuntimeException e) {
                last = e;
                if (!String.valueOf(e.getMessage()).contains("unknown to FrontBoard")) {
                    throw e;
                }
                try {
                    Thread.sleep(backoffMs * i);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }
}
