package core.reports;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * Snapshot of a finished run: failed and skipped tests by their qualified method name.
 */
public class TestResults {
    private final @Nonnull List<String> failedTests;
    private final @Nonnull List<String> skippedTests;
    private final int passedTestsCnt;

    public TestResults(
            @Nonnull List<String> failedTests,
            @Nonnull List<String> skippedTests,
            int passedTestsCnt) {
        this.failedTests = Collections.unmodifiableList(failedTests);
        this.skippedTests = Collections.unmodifiableList(skippedTests);
        this.passedTestsCnt = passedTestsCnt;
    }

    public @Nonnull List<String> getFailedTests() {
        return failedTests;
    }

    public @Nonnull List<String> getSkippedTests() {
        return skippedTests;
    }

    public int getPassedTestsCnt() {
        return passedTestsCnt;
    }
}
