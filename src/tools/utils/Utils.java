package tools.utils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;

/**
 * File, string and console helpers used by the runner, the log and ADB.
 */
public class Utils {

    public static @Nonnull Property parse(@Nonnull String property, char separator) {
        int index = property.indexOf(separator);
        if (index != -1) {
            return new Property(
                    property.substring(0, index),
                    property.substring(index + 1));
        } else {
            return new Property(property, "");
        }
    }

    public static void writeTo(@Nonnull File file, @Nonnull String content) throws IOException {

        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
        } else {
            file.delete();
        }

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    public static @Nonnull String makeHtmlAndOsSafe(@Nonnull String input) {
        String result = input.split("\n")[0];
        result = StringUtils.replace(result, "[", "_");
        result = StringUtils.replace(result, "]", "_");
        result = StringUtils.join(result.split("[?:,*%$#'\"!@ <>/+]"), "_");
        result = StringUtils.join(result.split("[_]+"), "_");
        result = StringUtils.join(StringUtils.split(result, ":"), ".");
        return result;
    }

    public static void forceMkdirs(@Nonnull File dir) {
        try {
            FileUtils.forceMkdir(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create directory [" + dir + "]", e);
        }
    }

    public static @Nonnull String htmlToSysOut(@Nonnull String htmlString) {
        htmlString = StringUtils.replace(htmlString, "<dd>at ","        at ");
        htmlString = StringUtils.replace(htmlString, "</dd>","\n");
        htmlString = htmlString.replaceAll("<[^>]*>", ""); // Removes all HTML tags
        return htmlString;
    }

    public static String toString(@Nonnull Collection<?> collection) {
        return toString(collection, null);
    }

    public static String toString(@Nonnull Collection<?> collection, @Nullable String delimiter) {
        if (delimiter == null) {
            delimiter = ", ";
        }
        return StringUtils.join(collection, delimiter);
    }

    public static void ensureExists(@Nonnull String path, boolean dir) {
        File file = new File(path);
        if (!file.exists()) {
            try {
                if (dir) {
                    FileUtils.forceMkdir(file);
                } else {
                    FileUtils.forceMkdir(file.getParentFile());
                    FileUtils.touch(file);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot create " + path, e);
            }
        }
    }

    public static String getMessageBlock(@Nonnull String message) {
        String[] lines = StringUtils.split(message, "\n");
        String line = getStarredLine(findLongestLine(lines));
        int length = line.length();
        StringBuilder middleBox = new StringBuilder();
        for (String nextLine : lines) {
            String middle = getMiddleLine(nextLine, length);
            middleBox.append(middle);
        }
        return line + middleBox + line + "\n";
    }

    private static int findLongestLine(@Nonnull String[] lines) {
        int length = 0;
        for (String line : lines) {
            if (line.length() > length) {
                length = line.length();
            }
        }
        if ( length % 2 != 0) {
            length ++;
        }
        return length;
    }

    private static final String prefixSuffix = "***********";

    private static String getMiddleLine(String msg, int size) {
        StringBuilder blankSpaces = new StringBuilder(" ");
        int length = (size - msg.length()) / 2 - prefixSuffix.length() - 1;
        while (length > 0) {
            blankSpaces.append(" ");
            length--;
        }
        return "\n" + prefixSuffix + blankSpaces + msg + blankSpaces + prefixSuffix;
    }

    private static String getStarredLine(int msgLength) {
        StringBuilder builder = new StringBuilder("\n");
        builder.append(prefixSuffix);
        int size = msgLength + 2;
        builder.append("*".repeat(Math.max(0, size)));
        builder.append(prefixSuffix);
        return builder.toString();
    }
}
