package core.utilities;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackgroundUtils {
    // reserved for TestListener's result tracking; any other task would delay the results
    public static final ExecutorService testResultTrackerExecutor
            = Executors.newSingleThreadExecutor(r -> new Thread(r, "Test-Result-Tracker"));
}
