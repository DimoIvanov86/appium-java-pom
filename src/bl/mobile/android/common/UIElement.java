package bl.mobile.android.common;

import bl.mobile.common.BaseUIElement;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;

import javax.annotation.Nonnull;

/**
 * Android UI element. Resource ids are passed in full (package:id/name), so elements of other
 * apps such as system dialogs are built the same way as the app's own.
 */
public class UIElement extends BaseUIElement {

    public static final UIElement
            TEXT_VIEW = new UIElement(By.xpath(".//android.widget.TextView"), "");

    protected UIElement(
            @Nonnull By by,
            @Nonnull String textByDesign,
            @Nonnull String description) {
        super(by, textByDesign, description);
    }

    public UIElement(
            @Nonnull By by,
            @Nonnull String description) {
        this(by, "", description);
    }

    public static UIElement byId(
            @Nonnull String resourceId,
            @Nonnull String text,
            @Nonnull String descr) {
        return new UIElement(By.id(resourceId), text, descr);
    }

    public static UIElement byId(
            @Nonnull String resourceId,
            @Nonnull String descr) {
        return byId(resourceId, "", descr);
    }

    public static UIElement byResourceIdAndText(
            @Nonnull String resourceId,
            @Nonnull String text,
            @Nonnull String descr) {
        final String xpathSingleQuote = ".//*[@resource-id='%s' and @text='%s']";
        final String xpath = text.contains("'") ? replaceQuotes(xpathSingleQuote) : xpathSingleQuote;
        final By by = By.xpath(String.format(
                xpath,
                resourceId,
                text));
        return new UIElement(by, text, descr);
    }

    public static UIElement byResourceIdAndTextContained(
            @Nonnull String resourceId,
            @Nonnull String text,
            @Nonnull String descr) {
        final String xpathSingleQuote = ".//*[@resource-id='%s' and contains(@text, '%s')]";
        final String xpath = text.contains("'") ? replaceQuotes(xpathSingleQuote) : xpathSingleQuote;
        final By by = By.xpath(String.format(
                xpath,
                resourceId,
                text));
        return new UIElement(by, text, descr);
    }

    public static UIElement byContentDescr(
            @Nonnull String contentDescription,
            @Nonnull String text,
            @Nonnull String descr) {
        return byElementAndContentDescr("*", contentDescription, text, descr);
    }

    public static UIElement byText(
            @Nonnull String text,
            @Nonnull String descr) {
        return byElementAndText("*", text, descr);
    }

    public static UIElement textViewByText(
            @Nonnull String text,
            @Nonnull String descr) {
        return byElementAndText("android.widget.TextView", text, descr);
    }

    public static UIElement textViewByContentDescr(
            @Nonnull String contentDescription,
            @Nonnull String text,
            @Nonnull String descr) {
        return byElementAndContentDescr("android.widget.TextView", contentDescription, text, descr);
    }

    public static UIElement textView(@Nonnull String description) {
        return new UIElement(TEXT_VIEW.by, description);
    }

    public static UIElement editTextByParentId(
            @Nonnull String parentResourceId,
            @Nonnull String text,
            @Nonnull String descr) {
        final By by = By.xpath(String.format(
                "//*[@resource-id='%s']//android.widget.EditText",
                parentResourceId));
        return new UIElement(by, text, descr);
    }

    public static UIElement btnByContentDescr(
            @Nonnull String contentDescription,
            @Nonnull String text,
            @Nonnull String descr) {
        return byElementAndContentDescr("android.widget.Button", contentDescription, text, descr);
    }

    public static UIElement btnByText(
            @Nonnull String text) {
        return byElementAndText("android.widget.Button", text, "'" + text + "' button");
    }

    public static UIElement byElementAndText(
            @Nonnull String element,
            @Nonnull String text,
            @Nonnull String descr) {
        final By by = By.xpath(String.format(
                ".//%s[@text='%s']",
                element,
                text));
        return new UIElement(by, text, descr);
    }

    public static UIElement byElementAndContentDescr(
            @Nonnull String element,
            @Nonnull String contentDescription,
            @Nonnull String text,
            @Nonnull String descr) {
        final By by = By.xpath(String.format(
                ".//%s[@content-desc='%s']",
                element,
                contentDescription));

        return new UIElement(by, text, descr);
    }

    public static UIElement byBy(
            @Nonnull By by,
            @Nonnull String text,
            @Nonnull String descr) {
        return new UIElement(by, text, descr);
    }

    public static UIElement byXpath(
            @Nonnull String xpath,
            @Nonnull String text,
            @Nonnull String descr) {
        return new UIElement(By.xpath(xpath), text, descr);
    }

    public static UIElement byXpath(
            @Nonnull String xpath,
            @Nonnull String descr) {
        return byXpath(xpath, "", descr);
    }

    public static String getShortId(By by) {
        return StringUtils.substringAfter(by.toString(), "/");
    }

    private static String replaceQuotes(String xpath) {
        return StringUtils.replace(xpath, "'", "\"");
    }
}
