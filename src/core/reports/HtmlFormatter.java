package core.reports;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static core.reports.HtmlFormatter.Color.BLACK;
import static core.reports.HtmlFormatter.Color.DARK_BLUE;
import static core.reports.HtmlFormatter.Color.GREEN;
import static core.reports.HtmlFormatter.Color.ORANGE;
import static core.reports.HtmlFormatter.Color.PURPLE;
import static core.reports.HtmlFormatter.Color.RED;
import static core.reports.HtmlFormatter.Formatter.BOLD;
import static core.reports.HtmlFormatter.Formatter.BOLD_ITALIC;
import static core.reports.HtmlFormatter.Formatter.BR;
import static core.reports.HtmlFormatter.Formatter.ITALIC;
import static core.reports.HtmlFormatter.Formatter.NO;
import static core.reports.HtmlFormatter.Size.H2;
import static core.reports.HtmlFormatter.Size.LARGE;
import static core.reports.HtmlFormatter.Size.PLUS_1;
import static core.reports.HtmlFormatter.Size.REGULAR;
import static core.reports.HtmlFormatter.Size.WORD_WRAP;

public class HtmlFormatter {

    private final @Nonnull Color txtColor;
    private final @Nonnull Size txtSize;
    private final @Nonnull Formatter txtFormatter;
    public static final HtmlFormatter INFO = new HtmlFormatter(BLACK, REGULAR, NO);
    public static final HtmlFormatter DEBUG = new HtmlFormatter(ORANGE, REGULAR, ITALIC);
    public static final HtmlFormatter PASSED = new HtmlFormatter(GREEN, REGULAR, BOLD_ITALIC);
    public static final HtmlFormatter ERROR = new HtmlFormatter(RED, REGULAR, BOLD_ITALIC);
    public static final HtmlFormatter EXCEPTION = new HtmlFormatter(RED, WORD_WRAP, NO);
    public static final HtmlFormatter TITLE = new HtmlFormatter(DARK_BLUE, H2, BOLD);
    public static final HtmlFormatter BLOCK = new HtmlFormatter(BLACK, REGULAR, BOLD);
    public static final HtmlFormatter MAIN_BLOCK = new HtmlFormatter(BLACK, PLUS_1, BOLD);
    public static final HtmlFormatter SECTION = new HtmlFormatter(DARK_BLUE, LARGE, BOLD);
    public static final HtmlFormatter ERROR_SECTION = new HtmlFormatter(RED, LARGE, BOLD);
    public static final HtmlFormatter NEW_LINE_EMPHASISED = new HtmlFormatter(PURPLE, REGULAR, BR);
    public static final HtmlFormatter HEALED = new HtmlFormatter(ORANGE, REGULAR, BOLD);

    private HtmlFormatter(
            @Nonnull Color txtColor,
            @Nonnull Size txtSize,
            @Nonnull Formatter txtFormatter) {

        this.txtColor = txtColor;
        this.txtSize = txtSize;
        this.txtFormatter = txtFormatter;
    }

    public enum Color {
        DARK_BLUE("DarkBlue"),
        RED("red"),
        GREEN("green"),
        PURPLE("purple"),
        ORANGE("orange"),
        BLACK("black");

        final @Nonnull String colorFont;

        Color(@Nonnull String color) {
            colorFont = String.format("<font color=\"%s\">%s</font>", color, "%s");
        }

        public String format(String text) {
            return String.format(colorFont, text);
        }

    }

    public enum Size {
        H2("h2"),
        LARGE("font size=\"+2\"", "font"),
        PLUS_1("font size=\"+1\"", "font"),
        REGULAR,
        WORD_WRAP("div style=\"word-wrap: break-word; width: 1200px\"", "div");

        final @Nonnull String sizeFont;

        Size() {
            this(null, null);
        }

        Size(@Nullable String tag)  {
            this(tag, null);
        }

        Size(@Nullable String start, @Nullable String end)  {
            sizeFont = start == null ? "%s" :
                       String.format("<%s>%s</%s>", start, "%s", (end != null ? end : start));
        }

        public String format(String text) {
            return String.format(sizeFont, text);
        }
    }

    public enum Formatter {
        BOLD("b"),
        ITALIC("i"),
        BOLD_ITALIC("b", "i"),
        NO,
        BR(false, "br");

        final @Nonnull String formatter;

        Formatter(boolean close, @Nonnull String... tags) {
            String formattedString = "%s";
            if (tags.length > 0) {
                for (String tag: tags) {
                    formattedString = String.format(
                            "<%s>%s%s", tag, formattedString, close ? "</" + tag + ">" : "");
                }
            }
            formatter = formattedString;
        }

        Formatter(@Nonnull String... tags) {
            this(true, tags);
        }

        @Nonnull
        public String format(String text) {
            return String.format(formatter, text);
        }
    }

    public @Nonnull String format(@Nonnull String input) {
        String formattedString = txtColor.format(input);
        formattedString = txtSize.format(formattedString);

        return txtFormatter.format(formattedString);
    }
}
