package bl.mobile.common;

import org.openqa.selenium.By;

import javax.annotation.Nonnull;

import static core.reports.HtmlFormatter.Color.PURPLE;

/**
 * A locator plus the text the element should show by design ("" when it is dynamic) and a
 * description used in the log.
 */
public class BaseUIElement {

    public final @Nonnull By by;
    public final @Nonnull String textByDesign;
    public final @Nonnull String description;
    public final @Nonnull String purpleDescr;

    protected BaseUIElement(
            @Nonnull By by,
            @Nonnull String textByDesign,
            @Nonnull String description) {
        this.by = by;
        this.textByDesign = textByDesign;
        this.description = description;
        this.purpleDescr = PURPLE.format(description);
    }

    public BaseUIElement(
            @Nonnull By by,
            @Nonnull String description) {
        this(by, "", description);
    }
}
