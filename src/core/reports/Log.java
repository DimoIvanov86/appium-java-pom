package core.reports;

import io.appium.java_client.AppiumDriver;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.asserts.SoftAssert;
import tools.utils.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import static core.reports.HtmlFormatter.BLOCK;
import static core.reports.HtmlFormatter.DEBUG;
import static core.reports.HtmlFormatter.ERROR;
import static core.reports.HtmlFormatter.ERROR_SECTION;
import static core.reports.HtmlFormatter.EXCEPTION;
import static core.reports.HtmlFormatter.Formatter.BR;
import static core.reports.HtmlFormatter.HEALED;
import static core.reports.HtmlFormatter.INFO;
import static core.reports.HtmlFormatter.MAIN_BLOCK;
import static core.reports.HtmlFormatter.PASSED;
import static core.reports.HtmlFormatter.SECTION;
import static core.reports.HtmlFormatter.TITLE;
import static core.reports.Log.DebugLevel.LOCAL;
import static core.reports.Log.DebugLevel.LOCAL_SCREENSHOTS;
import static core.reports.Log.DebugLevel.SCREENSHOTS;
import static core.reports.Log.DebugLevel.TRACE;
import static core.reports.Log.Section.CATCH;
import static core.utilities.StackTraceParser.getTestMethod;
import static core.utilities.StackTraceParser.frameworkStackTrace;
import static tools.utils.Utils.htmlToSysOut;
import static tools.utils.Utils.makeHtmlAndOsSafe;

/**
 * The per-test HTML step log (TestNG Reporter output), with sections, nested blocks, soft asserts
 * and screenshots. The DEBUG_LEVEL environment variable adds console output or more screenshots.
 */
public class Log {
    public enum DebugLevel {
        NONE,
        LOCAL,  // also print the log to the console
        SCREENSHOTS, // screenshot after every step, nothing on the console
        LOCAL_SCREENSHOTS, // both
        TRACE // the framework's own trace messages in the report, nothing on the console
    }

    public enum Section {
        PREPARATION("Preparation Steps", true),
        TEST_SCENARIO("Test Scenario", false),
        CATCH("Exception Caught", false),
        CLEANUP("Cleanup", true);

        public final String label;
        public final boolean collapsable;

        Section(String label, boolean collapsable) {
            this.label = label;
            this.collapsable = collapsable;
        }
    }

    public static final String SCREENSHOT_DIR = "screenshot/";
    public static final String TEST_OUTPUT_DIR = "test-output/";
    public static final String INDENT_STRING = "---";

    private static final Logger LOG = LoggerFactory.getLogger(Log.class);
    // read once per run, so an unknown value is reported once
    private static final DebugLevel DEBUG_LEVEL = readDebugLevel();

    private final SoftAssert softAssert = new SoftAssert();
    private final AppiumDriver driver;
    private final String screenshotsDir;
    private String testDescription = "";
    private int indentLevel = 0;
    private Section prevSection = null;

    public Log(@Nonnull AppiumDriver driver) {
        this.driver = driver;
        this.screenshotsDir = String.format("%s%s", SCREENSHOT_DIR, getTestMethod());
    }

    public void msg(@Nonnull String msg) {
        logLine(INFO.format(msg));
    }

    public void passed(@Nonnull String msg) {
        logLine(PASSED.format(msg));
    }

    public void error(@Nonnull String msg) {
        error(msg, null);
    }

    public void error(@Nullable String msg, @Nullable Throwable exc) {
        if (msg != null) {
            logLine(ERROR.format(msg));
        }

        // soft asserts fail at the end of the test, far from the problem, so a screenshot would mislead
        boolean softAssertFailure = exc instanceof AssertionError
                && exc.getMessage() != null
                && exc.getMessage().contains("The following asserts failed");
        if (!softAssertFailure) {
            makeScreenshot();
        }

        if (exc != null) {
            logException(exc);
        }
    }

    public void debug(@Nonnull String msg) {
        logLine(DEBUG.format(msg));
    }

    public void healed(@Nonnull String description, @Nonnull String oldLocator, @Nonnull String newLocator) {
        logLine(HEALED.format("SELF-HEALED: " + description
                + " | was: " + oldLocator
                + " | now: " + newLocator));
    }

    public void trace(String msg) {
        if (DEBUG_LEVEL == TRACE) {
            debug(msg);
        }
    }

    public void title(@Nonnull String msg) {
        logLine(TITLE.format(msg), false);
    }

    public void startBlock(@Nonnull String message) {
        logLine(BLOCK.format(message));
        indentLevel++;
    }

    public void endBlock() {
        indentLevel--;
    }

    // Soft assert: logs the result and lets the test continue; verifyTestStatus() fails the test later.
    public void verify(@Nonnull String logMsg, boolean condition) {
        if (condition) {
            passed(logMsg);
        } else {
            error(logMsg);
            softAssert.assertTrue(condition, logMsg);
        }
    }

    public void verify(@Nullable String logMsg, @Nonnull Throwable exc) {
        error(logMsg, exc);
        softAssert.assertTrue(false, logMsg);
    }

    // Hard assert: stops the test on failure.
    public void assertTrue(@Nonnull String logMsg, boolean condition) {
        if (condition) {
            passed(logMsg);
        } else {
            error(logMsg);
            Assert.assertTrue(condition, logMsg);
        }
    }

    private void logLine(String message) {
        logLine(message, true);
    }

    private void logLine(String message, boolean prefix) {
        String msg = "";
        if (prefix) {
            msg = msg + getPrefix();
        }
        msg = msg + message;

        Reporter.log(msg);

        if (DEBUG_LEVEL == LOCAL || DEBUG_LEVEL == LOCAL_SCREENSHOTS) {
            System.out.println(htmlToSysOut(msg));
        }

        if (DEBUG_LEVEL == LOCAL_SCREENSHOTS || DEBUG_LEVEL == SCREENSHOTS) {
            makeScreenshot();
        }
    }

    public void startTest(@Nonnull String description) {
        title(description);

        testDescription = description;

        startSection(Section.PREPARATION);
    }

    public void startTestScenario() {
        startSection(Section.TEST_SCENARIO);

        logLine(MAIN_BLOCK.format(testDescription));
    }

    public void verifyTestStatus() {
        softAssert.assertAll();
    }

    public void handleException(Throwable exc) {
        startSection(CATCH);
        logLine("");
        error(null, exc);
    }

    public void cleanUpSection() {
        if (prevSection != Section.CLEANUP) {
            startSection(Section.CLEANUP);
        }
    }

    public void endCleanupSection() {
        Reporter.log("</ol></details>");
    }

    public void startSection(Section section) {
        indentLevel = 0;

        if (prevSection != null) {
            if (prevSection.collapsable) {
                Reporter.log("</ol></details>");
            } else {
                Reporter.log("</ol>");
            }
        }

        if (section.collapsable) {
            collapsableSection(section.label);
        } else {
            if (section == CATCH) {
                errorSection(section.label);
            } else {
                section(section.label);
            }
        }
        Reporter.log("<ol type='1'>");

        prevSection = section;
    }

    // Writes straight to the report: going through logLine would take a screenshot of the
    // screenshot when DEBUG_LEVEL asks for one after every line.
    public @Nullable File makeScreenshot() {
        String filename = makeHtmlAndOsSafe(getTimestamp() + ".png");

        File screenshot = makeScreenshot(filename);
        if (screenshot == null) {
            return null;
        }

        Reporter.log(String.format(
                "<div class=\"content\"><img height=\"400\" src=\"%s\"/></div>",
                screenshotsDir + "/" + filename));
        return screenshot;
    }

    private @Nullable File makeScreenshot(@Nonnull String picName) {
        String destName = TEST_OUTPUT_DIR + screenshotsDir;

        try {
            File destDir = new File(destName);
            if (!destDir.exists()) {
                Utils.forceMkdirs(destDir);
            }

            File destFile = new File(destName, picName);
            File scrFile = driver.getScreenshotAs(OutputType.FILE);
            FileUtils.copyFile(scrFile, destFile);

            return destFile;
        } catch (Exception e) {
            // not through the step log: a log line may itself ask for a screenshot
            LOG.warn("Could not take a screenshot", e);
            return null;
        }
    }

    private void section(@Nonnull String msg) {
        logLine(SECTION.format(msg), false);
    }

    private void errorSection(@Nonnull String msg) {
        logLine(ERROR_SECTION.format(msg), false);
    }

    private void collapsableSection(String msg) {
        String message = String.format("<details><summary>%s (Click to view details)</summary>", SECTION.format(msg));
        logLine(message, false);
    }

    private void logException(Throwable exc) {
        String msg = shortenException(exc);
        logLine(
                EXCEPTION.format(msg) +
                        frameworkStackTrace(exc.getStackTrace(), true), false);
    }

    private static String shortenException(@Nonnull Throwable exc) {
        String[] msgParts = StringUtils.split(exc.toString(), "\n");
        String logMessage = msgParts[0];
        if (exc instanceof AssertionError) {
            logMessage = StringUtils.join(msgParts, "<br>&nbsp;&nbsp;&nbsp;&nbsp;");
        } else if (exc instanceof NoSuchElementException || exc instanceof StaleElementReferenceException) {
            for (int i = 1; i < msgParts.length; i++) {
                if (msgParts[i].startsWith("Command")) {
                    logMessage += BR.format(msgParts[i]);
                    break;
                }
            }
        }

        return logMessage;
    }

    private String getPrefix() {
        return String.format("<li>[%s] %s ", getTimestamp(), getIndentPrefix());
    }

    private String getIndentPrefix() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < indentLevel; i++) {
            result.append(INDENT_STRING);
        }

        return result.toString();
    }

    private static String getTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        LocalDateTime now = LocalDateTime.now();

        return now.format(formatter);
    }

    private static DebugLevel readDebugLevel() {
        String value = System.getenv("DEBUG_LEVEL");
        if (value == null || value.trim().isEmpty()) {
            return DebugLevel.NONE;
        }
        try {
            return DebugLevel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            LOG.warn("Unknown DEBUG_LEVEL '{}', expected one of {}; using NONE",
                    value, Arrays.toString(DebugLevel.values()));
            return DebugLevel.NONE;
        }
    }
}
