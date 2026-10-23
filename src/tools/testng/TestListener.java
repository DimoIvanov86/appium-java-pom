package tools.testng;

import core.utilities.BackgroundUtils;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects passed, failed and skipped tests. TestNG calls back on its worker threads, so all
 * state is read and written only on the single Test-Result-Tracker thread (BackgroundUtils).
 */
public class TestListener implements ITestListener {
    private static final Logger LOG = LoggerFactory.getLogger(TestListener.class);

    @GuardedBy("Test-Result-Tracker")
    private final @Nonnull List<String> failedTests = new ArrayList<>();

    @GuardedBy("Test-Result-Tracker")
    private final @Nonnull List<String> skippedTests = new ArrayList<>();

    @GuardedBy("Test-Result-Tracker")
    private int passedTests = 0;

    @GuardedBy("Test-Result-Tracker")
    private final @Nonnull Map<String, Map<String, List<String>>> testsBySectionAndDevice = new HashMap<>();

    @Override
    public void onTestSuccess(ITestResult result) {
        BackgroundUtils.testResultTrackerExecutor.submit(() -> {
            passedTests++;
        });
    }

    @Override
    public void onTestFailure(ITestResult result) {
        BackgroundUtils.testResultTrackerExecutor.submit(() -> {
            failedTests.add(result.getMethod().getQualifiedName());
        });
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        BackgroundUtils.testResultTrackerExecutor.submit(() -> {
            skippedTests.add(result.getMethod().getQualifiedName());
        });
    }

    @Override
    public void onStart(ITestContext context) {
        final String runningSuite = context.getSuite().getXmlSuite().getFileName();
        LOG.info("[{}] Running suite {}", context.getName(), runningSuite);
    }

    @Override
    public void onFinish(ITestContext context) {
        BackgroundUtils.testResultTrackerExecutor.submit(() -> {
            final String device = context.getCurrentXmlTest().getParameter("deviceName");
            fillDeviceInfo(context.getPassedTests().getAllResults(), "PASSED", device);
            fillDeviceInfo(context.getFailedTests().getAllResults(), "FAILED", device);
            fillDeviceInfo(context.getSkippedTests().getAllResults(), "SKIPPED", device);
        });
    }

    public @Nonnull List<String> skippedTests() {
        return List.copyOf(skippedTests);
    }

    public @Nonnull List<String> failedTests() {
        return List.copyOf(failedTests);
    }

    public int passedTestCount() {
        return passedTests;
    }

    public void printTestResults() {
        StringBuilder results = new StringBuilder("Results by device:");
        for (String section : testsBySectionAndDevice.keySet()) {
            results.append(String.format("%n%s", section));
            Map<String, List<String>> testsByDevice = testsBySectionAndDevice.get(section);
            for (String device : testsByDevice.keySet()) {
                results.append(String.format("%n    %s", device));
                for (String test : testsByDevice.get(device)) {
                    results.append(String.format("%n        %s", test));
                }
            }
        }
        LOG.info(results.toString());
    }

    private void fillDeviceInfo(Set<ITestResult> results, String sectionName, String device) {
        if (results.isEmpty()) {
            return;
        }
        final List<String> deviceTests =
                testsBySectionAndDevice.computeIfAbsent(sectionName, key -> new HashMap<>())
                         .computeIfAbsent(device, key -> new ArrayList<>());

        for (ITestResult testResult : results) {
            deviceTests.add(testResult.getTestClass().getRealClass().getSimpleName()
                    + "." + testResult.getMethod().getMethodName());
        }
    }
}
