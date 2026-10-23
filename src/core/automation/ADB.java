package core.automation;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.testng.Output;
import tools.utils.Utils;

import static core.utilities.RandomValuesGenerator.getDateTime;
import static tools.config.Config.ADB_PORT_DEFAULT;
import static tools.config.Config.ADB_PORT_PROPERTY;
import static tools.testng.Output.DEVICE_LOGS_DIR;

public class ADB {

    private static final Logger LOG = LoggerFactory.getLogger(ADB.class);

    public static final String LOG_DATE_FORMAT = "MM-dd HH:mm:ss.SSS";
    private static final String
            ADB_DEVICE_COMMAND = adbExecutable() + " -P " + System.getProperty(ADB_PORT_PROPERTY, ADB_PORT_DEFAULT);

    private final String prefixADB;

    public ADB(String udid) {
        prefixADB = ADB_DEVICE_COMMAND + " -s " + udid + " ";
    }

    public String clearAppData(String app) {
        return Utils.toString(executeCommand(prefixADB + "shell pm clear " + app, 30), "\n");
    }

    public void startApp(String app, String activity) {
        executeCommand(prefixADB + "shell am start -n " + app + "/" + activity, 15);
    }

    public String stopApp(String app) {
        return Utils.toString(executeCommand(prefixADB + "shell am force-stop " + app, 15), "\n");
    }

    public String clearUIAutomatorPort(int port) {
        return Utils.toString(executeCommand(prefixADB + " forward --remove tcp:" + port, 10), "\n");
    }

    public String stopAndClearData(String app) {
        String result = stopApp(app) + "\n";
        return result + clearAppData(app);
    }

    public void uninstall(String app) {
        executeCommand(prefixADB + "uninstall " + app, 60);
    }

    public static void logcat(
            @Nonnull String testName,
            @Nonnull String udid,
            @Nonnull String startTimestamp,
            @Nonnull String endTimestamp) {

        String phone = StringUtils.split(udid, ":")[0];
        File dir = new File(DEVICE_LOGS_DIR + phone + "/" + testName + "/");
        Utils.ensureExists(dir.getAbsolutePath(), true);
        String name = "/logs" + getDateTime("yyyy-MM-dd-HHmmss.SSS") + ".txt";
        String[] adbCommand = StringUtils.split(ADB_DEVICE_COMMAND, " ");
        List<String> command = new ArrayList<>(Arrays.asList(adbCommand));
        command.addAll(Arrays.asList("-s", udid, "logcat", "-d", "-t", startTimestamp, "-b", "all"));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir);
        pb.redirectErrorStream(true);
        Process p;
        try {
            File file = new File(dir.getAbsolutePath() + name);
            Utils.ensureExists(file.getAbsolutePath(), false);
            p = pb.start();
            LOG.info(Utils.toString(pb.command()));
            new Thread(new ProcessTestRunnable(p, file, startTimestamp, endTimestamp)).start();

            if (!ADB.waitProcessToFinish(p, 45)) {
                LOG.warn("logcat still running after 45 s, killing it");
                p.destroyForcibly();
            }
        } catch (IOException | ParseException e) {
            // the device log is a diagnostic extra; missing it must not fail the test
            LOG.warn("Could not save the device log of " + testName, e);
        }
    }

    public String getAppVersion(String appPackage) {
        String command = prefixADB + "shell dumpsys package " + appPackage;
        List<String> result = executeCommand(command, 30, "versionName");
        if (result.isEmpty()) {
            throw new IllegalStateException(appPackage + " is not installed on the device, no versionName found");
        }
        return result.get(0);
    }

    // false if the process is still running after the timeout
    private static boolean waitProcessToFinish(Process proc, int secondsToFinish) {
        try {
            return proc.waitFor(secondsToFinish, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static List<String> executeCommand(
            @Nonnull String command,
            int secondsToFinish) {
        return executeCommand(command, secondsToFinish, null);
    }

    private static List<String> executeCommand(@Nonnull String command, int secondsToFinish, @Nullable String grep) {
        File output = null;
        try {
            // one file per call: parallel devices run adb at the same time
            output = File.createTempFile("adb-", ".txt");
            LOG.info(command);
            ProcessBuilder pb = new ProcessBuilder(StringUtils.split(command, " "));
            Utils.ensureExists(Output.RESULTS_DIR, true);
            pb.directory(new File(Output.RESULTS_DIR));
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.to(output));
            Process proc = pb.start();
            if (!ADB.waitProcessToFinish(proc, secondsToFinish)) {
                LOG.warn("ADB command timed out after {} s, killing the process", secondsToFinish);
                proc.destroyForcibly();
            }

            List<String> result = new ArrayList<>(FileUtils.readLines(output, "UTF-8"));
            if (grep != null) {
                result.removeIf(line -> !line.contains(grep));
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot execute ADB command " + command, e);
        } finally {
            if (output != null) {
                output.delete();
            }
        }
    }

    private static String adbExecutable() {
        String androidHome = System.getenv("ANDROID_HOME");
        // without ANDROID_HOME, rely on adb being on the PATH
        return androidHome == null || androidHome.isEmpty() ? "adb" : androidHome + "/platform-tools/adb";
    }

    // Reads logcat's output and keeps only the lines between the test's start and end time.
    static class ProcessTestRunnable implements Runnable {
        private final Process p;
        private final File destination;
        private final Date start;
        private final Date end;
        private final SimpleDateFormat format = new SimpleDateFormat("yyyy-" + LOG_DATE_FORMAT);
        private final String year = getDateTime("yyyy-");

        ProcessTestRunnable(
                Process p,
                File destination,
                String start,
                String end) throws ParseException {

            this.p = p;
            this.destination = destination;
            this.start = format.parse(year + start);
            this.end = format.parse(year + end);
        }

        public void run() {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {

                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("--")) {
                        builder.append(line).append("\n");
                    } else if (line.length() < LOG_DATE_FORMAT.length() || !Character.isDigit(line.charAt(0))) {
                        // adb status lines such as "- waiting for device -" carry no timestamp
                        continue;
                    } else {
                        String string = line.substring(0, LOG_DATE_FORMAT.length());
                        try {
                            Date date = format.parse(year + string);
                            if (date.compareTo(start) >= 0 && date.compareTo(end) <= 0) {
                                builder.append(line).append("\n");
                            }
                        } catch (ParseException e) {
                            LOG.warn("Stopping log capture: no timestamp in line [{}]", line, e);
                            break;
                        }
                    }
                }
                Utils.writeTo(destination, builder.toString());
            } catch (IOException e) {
                LOG.warn("Could not write the device log to " + destination, e);
            }
            p.destroy();
        }
    }
}
