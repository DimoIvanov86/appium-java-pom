# appium-java-pom

Page-object test framework for Android and iOS apps: Java 11, Gradle, TestNG and the Appium
java-client. It contains one demo test per platform for the Wikipedia app; replace them with your
own screens and tests.

- All Appium calls go through `DriverWrapper` (Android) or `IOSDriverWrapper` (iOS), which log
  every step.
- Screens extend `AbstractScreen` / `IOSAbstractScreen` and define their elements with `UIElement` /
  `IOSUIElement`. Verification goes through `IVerifiable`.
- Each test writes an HTML step log to `test-output/` with sections for preparation, scenario,
  exception and cleanup, and a screenshot on every error.
- `./gradlew startTests` runs a TestNG suite and writes a summary for CI to `results/output.txt`.
- Self-healing locators are available but off by default (see below).

## Requirements

- JDK 11 (the Gradle wrapper is included; no local Gradle needed)
- Appium server 2 or later with the drivers you need:
  `appium driver install uiautomator2` and/or `appium driver install xcuitest`
- Android: Android SDK platform-tools. `adb` is taken from `$ANDROID_HOME/platform-tools`, or from
  the PATH when `ANDROID_HOME` is not set; a device or emulator with the app installed
- iOS: macOS with Xcode; a simulator, or a real device with WebDriverAgent signed by your team
  (`-Ppom.xcodeOrgId=<team id> -Ppom.xcodeSigningId="Apple Development"` and `iosWdaBundleId` in the config)

## Project layout

```
src/core/automation       driver wrappers, capabilities (Drivers), adb, self-healing
src/core/reports          HTML step log (Log), run results
src/core/utilities        stack-trace shortening, random test data
src/tools                 startTests entry point, suite runner, result listener, run summary, config constants
src/bl/mobile/common      platform-independent page-object base (BaseAbstractScreen, IVerifiable)
src/bl/mobile/android     Android page objects (common/ holds the base classes)
src/bl/mobile/ios         iOS page objects, IOS-prefixed (common/ holds the base classes)
src/tests/android         Android tests (extend BaseTest)
src/tests/ios             iOS tests (extend IOSBaseTest)
config/config.properties  app ids and Appium URL - no secrets
suites/                   TestNG suite files: which tests run on which device
```

Everything, tests included, is in the main source set, so `startTests` runs a suite with a plain
`JavaExec`.

## Running

Start the Appium server (`appium`), connect a device, put its UDID in the suite file, then:

```bash
# Android
./gradlew startTests -Ppom.testNGxml=suites/local.xml -Ppom.env=config/config.properties -Ppom.debug=true

# iOS
./gradlew startTests -Ppom.testNGxml=suites/local-ios.xml -Ppom.env=config/config.properties

# compile only
./gradlew compileJava
```

| Property | Meaning |
|---|---|
| `-Ppom.testNGxml=<file>` | suite file to run (required) |
| `-Ppom.env=<file>` | config file; overrides the suite's `config` parameter. Without either, `config/config.properties` |
| `-Ppom.debug=true` | print the suite as it is run |
| `-Ppom.appiumPort=<n>` | Appium server port (default 4723) |
| `-Ppom.adbPort=<n>` | adb server port (default 5037) |
| `-Ppom.selfHealing=true` | turn self-healing on for this run |
| `-Ppom.healingModel=<model id>` | model used for self-healing (default `claude-sonnet-5-5`) |
| `-Ppom.stackTracePackages=a.,b.` | package prefixes kept in shortened stack traces (default `bl.,core.,tests.,tools.`) |
| `-Ppom.wdaConnectionTimeout=<ms>` | per-request WebDriverAgent timeout (default 180000; lower it to fail fast while debugging) |
| `-Ppom.xcodeOrgId=<team id>`, `-Ppom.xcodeSigningId=<identity>` | WebDriverAgent signing for real iOS devices |

All `-Ppom.*` properties are passed to the test JVM as system properties of the same name. Names
in upper case are environment variables.

The environment variable `DEBUG_LEVEL` (read only from the environment, once per run) controls
extra output. An empty or unknown value means `NONE`; an unknown value is also reported as a warning.

| `DEBUG_LEVEL` | Effect |
|---|---|
| `NONE` (default) | step log in the HTML report only, screenshot on errors |
| `LOCAL` | also prints every step-log line to the console |
| `SCREENSHOTS` | adds a screenshot to the report after every step; nothing extra on the console |
| `LOCAL_SCREENSHOTS` | both of the above |
| `TRACE` | adds the framework's own trace messages (each element lookup, presence check and text or attribute read) to the report; nothing extra on the console |

To run one test, edit the `<class>` and `<include>` of a suite file. Several devices in parallel:
add one `<test>` block per device (each Android device with its own `port`).

Results: the TestNG HTML report and screenshots in `test-output/`, the run summary in
`results/output.txt`, Android logcat per test in `results/logs/`.

## Configuration

`config/config.properties` holds what identifies the app and the Appium server:

| Key | Used for |
|---|---|
| `urlHost` | Appium URL; `%s` is replaced with the Appium port |
| `appPackage`, `appActivity` | Android app under test |
| `iosBundleId` | iOS app under test |
| `iosAppPath` | optional `.ipa`/`.app` reinstalled before each session for a clean state |
| `iosWdaBundleId` | optional WebDriverAgent bundle id for real-device signing |
| `selfHealing` | `true` to enable self-healing locators |

Never put credentials in it. Read secrets from environment variables in the base test (for
example `System.getenv("APP_TEST_PASSWORD")`) and document the variable here.

## Adding a screen

One screen = one page-object class. Android: `src/bl/mobile/android/<feature>/<Name>Screen.java`
extending `AbstractScreen`; iOS: `src/bl/mobile/ios/<feature>/IOS<Name>Screen.java` extending
`IOSAbstractScreen`.

```java
public class SettingsScreen extends AbstractScreen {

    private static final UIElement
            TITLE = byId("org.example.app:id/toolbar_title", "Settings", "screen title"),
            DARK_MODE = byId("org.example.app:id/dark_mode_switch", "'Dark mode' switch");

    public SettingsScreen(@Nonnull DriverWrapper driverWrapper) {
        super(driverWrapper);
        assertDisplayed();
    }

    public SettingsScreen toggleDarkMode() {
        driverWrapper.click(DARK_MODE);
        return this;
    }

    @Override
    public void verify() {
        log.startBlock("Verify the settings screen");
        verify(TITLE);
        verify(DARK_MODE);
        log.endBlock();
    }

    @Override
    public void assertDisplayed() {
        assertDisplayed("Settings screen has been displayed");
    }

    @Override
    public boolean isDisplayed() {
        return driverWrapper.onScreen(TITLE);
    }
}
```

Methods return `this` when the action stays on the same screen and the next screen object when it
navigates. Prefer resource ids (Android) and accessibility names/labels (iOS) over XPath. Wait only
through the wrapper (`waitToAppear`, `waitToDisappear`, `findElement(..., timeout)`), never with
`Thread.sleep` in a page object or test.

## Adding a test

```java
public class SettingsTests extends BaseTest {

    @Test
    @Parameters({"udid", "port", "deviceName"})
    public void darkModeCanBeSwitchedOn(String udid, String port, String deviceName) {
        DriverWrapper driverWrapper = new DriverWrapper(udid, port, deviceName, androidConfig);
        driverWrapper.log.startTest("Dark mode can be switched on from Settings");
        try {
            SettingsScreen settings = /* navigate to the screen (Preparation section) */;

            driverWrapper.log.startTestScenario();
            driverWrapper.log.startBlock("Switch dark mode on");
            settings.toggleDarkMode();
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
```

- `log.verify(msg, condition)` is a soft check, reported by `verifyTestStatus()`;
  `log.assertTrue(msg, condition)` stops the test.
- Every test starts from a fresh app state, so grant or deny the system permission dialogs
  explicitly: `screen.handleCamera()`, `handleMic()`, `handleNotifications()`, `handleLocation()`.
- Add the test to a suite file to run it.

## Self-healing locators (opt-in)

When a locator stops matching (typically after an app update renamed an id), the framework can
ask the Claude API for a replacement, using the element's description and the current page source.
It is off by default. To turn it on:

1. Export the API key: `export ANTHROPIC_API_KEY=...` (it is read only from the environment).
2. Set `selfHealing=true` in the config, or pass `-Ppom.selfHealing=true` for one run.

If it is enabled but the key is missing, the run prints a notice and continues without healing.
Only id and accessibility-id locators are healed (XPath encodes structure a replacement could not
preserve); a locator the API cannot fix is not retried. Healed locators are logged in orange in
the step log, cached in `test-output/self-healing-cache.json`, summarised at the end of the run
and listed in `test-output/self-healing-report.html`. A heal is a hint to update the page object,
not a fix: copy the healed locator into the code. Note that healing sends the stripped page source
of the failing screen to the API.

## The demo

`tests/android/search/SearchTests` and `tests/ios/search/IOSSearchTests` open the Wikipedia app
(`org.wikipedia` / `org.wikimedia.wikipedia`), skip the onboarding if it is shown, search for
"Appium", open the first result and verify the article title. Their locators were written
without a device and are unverified; each is marked in its page object. Confirm them against
the app's page source (Appium Inspector) before relying on the demo.
