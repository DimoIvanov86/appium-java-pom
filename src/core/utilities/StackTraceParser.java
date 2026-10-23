package core.utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static tools.config.Config.STACK_TRACE_PACKAGES_DEFAULT;
import static tools.config.Config.STACK_TRACE_PACKAGES_PROPERTY;

/**
 * Shortens stack traces to the frames of this project, so a report shows where in the test or
 * page object a failure happened instead of pages of Selenium/Appium internals.
 * <p>
 * Which classes count as "this project" is set by package prefix: the system property
 * {@code pom.stackTracePackages} (comma-separated), defaulting to this skeleton's packages.
 */
public final class StackTraceParser {

    private static final String TESTS_PACKAGE = "tests.";
    private static final List<String> FRAMEWORK_PACKAGES = Arrays.asList(
            System.getProperty(STACK_TRACE_PACKAGES_PROPERTY, STACK_TRACE_PACKAGES_DEFAULT).split("\\s*,\\s*"));

    private StackTraceParser() {
    }

    private static boolean isFrameworkClass(String clazz) {
        for (String prefix : FRAMEWORK_PACKAGES) {
            if (clazz.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static @Nonnull String frameworkStackTrace(@Nonnull StackTraceElement[] trace, boolean html) {
        List<StackTraceElement> framework = new ArrayList<>();
        for (StackTraceElement stackTraceElement : trace) {
            String clazz = stackTraceElement.getClassName();
            // everything below TestNG is the runner's own machinery
            if (clazz.startsWith("org.testng")) {
                break;
            } else if (isFrameworkClass(clazz)) {
                framework.add(stackTraceElement);
            }
        }
        return stackTrace(framework.toArray(new StackTraceElement[0]), html);
    }

    private static String prefix(boolean html) {
        return html ? "<dd><font color='red'>at " : "        at ";
    }

    private static String newLine(boolean html) {
        return html ? "</font></dd>" : "\n";
    }

    public static @Nonnull String stackTrace(@Nullable StackTraceElement[] trace, boolean html) {

        if (trace == null) {
            trace = Thread.currentThread().getStackTrace();
        }
        String newLine = newLine(html);
        String prefix = prefix(html);

        StringBuilder builder = new StringBuilder();
        for (StackTraceElement stackTraceElement : trace) {
            builder.append(prefix);
            if (html) {
                builder.append(htmlFrame(stackTraceElement));
            } else {
                builder.append(stackTraceElement);
            }
            builder.append(newLine);
        }
        return builder.toString();
    }

    // Highlights the test's own frames and dims library frames, so the eye lands on the test line.
    private static String htmlFrame(@Nonnull StackTraceElement frame) {
        String className = frame.getClassName();
        if (className.startsWith(TESTS_PACKAGE)) {
            return "<mark>" + frame + "</mark>";
        }
        if (className.startsWith("org")
                || className.startsWith("sun")
                || className.startsWith("java")
                || className.startsWith("io.appium")) {
            return "<i>" + frame + "</i>";
        }
        return frame.toString();
    }

    /**
     * Returns the name of the test method on the current call stack, or "" when called outside a test
     */
    public static @Nonnull String getTestMethod() {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();

        for (StackTraceElement stackTraceElement : stackTraceElements) {
            String className = stackTraceElement.getClassName();
            if (className.startsWith(TESTS_PACKAGE)) {
                return stackTraceElement.getMethodName();
            }
        }
        return "";
    }
}
