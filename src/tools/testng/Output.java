package tools.testng;

import core.reports.TestResults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.utils.Utils;

import javax.annotation.Nonnull;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

/**
 * Writes results/output.txt - a properties file with the run summary that a CI job can read
 * to name the build status, without parsing the TestNG report.
 */
public final class Output {
    private static final Logger LOG = LoggerFactory.getLogger(Output.class);

    public static final String RESULTS_DIR = "results/";
    public static final String OUTPUT_FILE_NAME = RESULTS_DIR + "output.txt";
    public static final String DEVICE_LOGS_DIR = RESULTS_DIR + "logs/";

    public enum RunStatus {
        SUCCESS,
        SKIPPED_TESTS,
        FAILURE
    }

    public static void write(
            @Nonnull RunStatus status,
            @Nonnull String msg,
            @Nonnull TestResults results) throws IOException {

        Properties output = new Properties();
        output.setProperty("FINAL_STATUS", status.name());
        output.setProperty("FINAL_STATUS_MSG", msg);
        output.setProperty("TOTAL_PASSED", String.valueOf(results.getPassedTestsCnt()));
        output.setProperty("TOTAL_FAILED", String.valueOf(results.getFailedTests().size()));
        output.setProperty("TOTAL_SKIPPED", String.valueOf(results.getSkippedTests().size()));
        output.setProperty("FAILED_TESTS", Utils.toString(results.getFailedTests(), ","));
        output.setProperty("SKIPPED_TESTS", Utils.toString(results.getSkippedTests(), ","));

        Utils.ensureExists(RESULTS_DIR, true);
        try (FileWriter writer = new FileWriter(OUTPUT_FILE_NAME)) {
            output.store(writer, "Run summary for CI");
        }
        LOG.info(Utils.getMessageBlock(status + ": " + msg));
    }

    private Output() {
    }
}
