package tools.testng;

import core.automation.selfhealing.LocatorHealer;
import core.reports.TestResults;
import core.utilities.BackgroundUtils;
import org.testng.TestNG;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.xml.XmlSuite;
import org.testng.xml.internal.Parser;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.testng.internal.ExitCode.HAS_NO_TEST;
import static org.testng.internal.ExitCode.hasFailure;
import static tools.config.Config.DEFAULT_CONFIG_PATH;
import static tools.testng.Output.RunStatus.FAILURE;
import static tools.testng.Output.RunStatus.SKIPPED_TESTS;
import static tools.testng.Output.RunStatus.SUCCESS;

/**
 * Used to prepare testNG run and to execute it.
 */
public final class Runner {
    private static final Logger LOG = LoggerFactory.getLogger(Runner.class);


    /**
     * Runs the suite file and writes the run summary.
     *
     * @return the TestNG exit status, for the process exit code
     */
    public static int runSuite(
            @Nonnull String xmlPath,
            @Nullable Map<String, String> suiteParams,
            boolean debug
    ) throws IOException, ExecutionException, InterruptedException {

        Parser parser = new Parser(xmlPath);
        List<String> suiteFiles = writeSuitesWithParams(xmlPath, parser.parseToList(), suiteParams, debug);

        final TestNG runner = new TestNG();
        final TestListener listener = new TestListener();
        runner.addListener(listener);
        runner.setTestSuites(suiteFiles);
        runner.run();

        // Read the results on the tracker thread - the only thread that ever touches them.
        Future<TestResults> testResultsFuture = BackgroundUtils.testResultTrackerExecutor.submit(() -> {
            listener.printTestResults();
            return new TestResults(listener.failedTests(), listener.skippedTests(), listener.passedTestCount());
        });

        final int runStatus = runner.getStatus();
        setBuildStatus(runStatus, testResultsFuture.get());
        LocatorHealer.printHealingSummary();
        return runStatus;
    }

    /**
     * Command-line parameters (-Ppom.env, -Ppom.selfHealing) override the suite's own parameters,
     * so the same suite file runs against any config. TestNG only reads files, hence the copies.
     */
    private static @Nonnull List<String> writeSuitesWithParams(
            @Nonnull String xmlPath,
            @Nonnull List<XmlSuite> suites,
            @Nullable Map<String, String> suiteParams,
            boolean debug
    ) throws IOException {

        List<String> result = new ArrayList<>();
        for (XmlSuite suite : suites) {
            Map<String, String> params = suite.getParameters();
            if (suiteParams != null) {
                params.putAll(suiteParams);
            }
            // only a suite without its own config gets the default one
            params.putIfAbsent("config", DEFAULT_CONFIG_PATH);
            suite.setParameters(params);
            String xml = suite.toXml();
            if (debug) {
                LOG.info("Suite to run:\n{}", xml);
            }

            String fileName = xmlPath + "_" + suite.getName() + "_.xml";
            try (FileWriter writer = new FileWriter(fileName)) {
                writer.write(xml);
            }
            result.add(fileName);
        }

        return result;
    }

    private static void setBuildStatus(int runStatus, @Nonnull TestResults results) throws IOException {
        int failed = results.getFailedTests().size();
        int skipped = results.getSkippedTests().size();
        int passed = results.getPassedTestsCnt();
        int total = failed + skipped + passed;

        Output.RunStatus status;
        String msg;
        if (runStatus == HAS_NO_TEST) {
            status = FAILURE;
            msg = "NO TEST FOUND in the suite";
        } else if (hasFailure(runStatus)) {
            status = FAILURE;
            msg = String.format("Failed %d (%d%%) from %d tests (Passed %d, Failed %d, Skipped %d)",
                    failed, getPercent(failed, total), total, passed, failed, skipped);
        } else if (skipped > 0) {
            status = SKIPPED_TESTS;
            msg = String.format("Passed %d, Skipped %d, Total %d", passed, skipped, total);
        } else {
            status = SUCCESS;
            msg = "All Tests (" + total + ") passed";
        }

        Output.write(status, msg, results);
    }

    private static int getPercent(int failed, int total) {
        return (int) Math.round((failed * 100.0) / total);
    }
}
