package tools.testng;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.utils.Property;
import tools.utils.Utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Entry point of the Gradle startTests task.
 */
public class RunMobileTestsCmd {

    private static final Logger LOG = LoggerFactory.getLogger(RunMobileTestsCmd.class);
    private static final int EXIT_RUNNER_ERROR = 145;

    private static final class Input {
        private final Map<String, String> suiteParams = new HashMap<>();
        private boolean debug = false;
        private String xmlPath;
    }

    public static void main(String[] args) {
        try {
            LOG.info("Starting TestNG with the parameters {}", Utils.toString(Arrays.asList(args)));

            Input input = parseArguments(args);
            if (input.xmlPath == null || input.xmlPath.isEmpty()) {
                throw new IllegalArgumentException(
                        "No suite file given - pass -Ppom.testNGxml=<path>, e.g. suites/local.xml");
            }

            int exitCode = Runner.runSuite(input.xmlPath, input.suiteParams, input.debug);
            System.exit(exitCode);
        } catch (Throwable exc) {
            LOG.error("The test run could not start or finish", exc);
            System.exit(EXIT_RUNNER_ERROR);
        }
    }

    private static Input parseArguments(String[] args) {
        Input input = new Input();
        for (String arg : args) {
            Property p = Utils.parse(arg, '=');
            switch (p.key) {
                case "--testNGXml":
                    input.xmlPath = p.value;
                    break;
                case "--env":
                    if (!p.value.isEmpty()) {
                        input.suiteParams.put("config", p.value);
                    }
                    break;
                case "--debug":
                    input.debug = true;
                    break;
                case "--selfHealing":
                    if (!p.value.isEmpty()) {
                        input.suiteParams.put("selfHealing", p.value);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unknown argument " + arg);
            }
        }
        return input;
    }
}
