package bl.mobile.ios.common.base;

import bl.mobile.common.BaseUIElement;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static bl.mobile.ios.common.base.IOSUIElement.Attribute.LABEL;
import static bl.mobile.ios.common.base.IOSUIElement.Attribute.NAME;
import static bl.mobile.ios.common.base.IOSUIElement.Type.ANY;
import static bl.mobile.ios.common.base.IOSUIElement.Type.BUTTON;
import static bl.mobile.ios.common.base.IOSUIElement.Type.LINK;
import static bl.mobile.ios.common.base.IOSUIElement.Type.STATIC_TEXT;
import static bl.mobile.ios.common.base.IOSUIElement.Type.TEXT_FIELD;
import static bl.mobile.ios.common.base.IOSUIElement.Type.TEXT_VIEW;

/**
 * iOS UI element. Class chains are the default because they are the fastest XCUITest strategy;
 * predicate and XPath factories are fallbacks.
 */
public class IOSUIElement extends BaseUIElement {

    public enum Type {
        ANY("*"),
        TEXT_FIELD("XCUIElementTypeTextField"),
        STATIC_TEXT("XCUIElementTypeStaticText"),
        TEXT_VIEW("XCUIElementTypeTextView"),
        LINK("XCUIElementTypeLink"),
        BUTTON("XCUIElementTypeButton");

        public final String xcuiElement;

        Type(String element) {
            xcuiElement = element;
        }
    }

    public enum Attribute {
        NAME, LABEL
    }

    private IOSUIElement(
            @Nonnull By by,
            @Nonnull String textByDesign,
            @Nonnull String description) {
        super(by, textByDesign, description);
    }

    private IOSUIElement(
            @Nonnull String classChain,
            @Nonnull String textByDesign,
            @Nonnull String description) {
        super(AppiumBy.iOSClassChain(classChain), textByDesign, description);
    }

    public static IOSUIElement byAttribute(
            @Nonnull Type type,
            @Nonnull Attribute attribute,
            @Nonnull String value,
            @Nonnull String text,
            @Nonnull String descr) {
        final String classChain = String.format(
                "**/%s[`%s == \"%s\" AND visible == 1`]",
                type.xcuiElement,
                attribute.toString().toLowerCase(),
                value);
        return new IOSUIElement(classChain, text, descr);
    }

    // byName without "visible == 1", for elements WDA wrongly reports as invisible.
    public static IOSUIElement byPlainName(@Nonnull String name, @Nonnull String descr) {
        return byClassChain(
                String.format("**/*[`name == \"%s\"`]", name), descr);
    }

    public static IOSUIElement byPlainName(@Nonnull String name) {
        return byPlainName(name, "");
    }

    public static IOSUIElement byPlainAttribute(
            @Nonnull Type type,
            @Nonnull Attribute attribute,
            @Nonnull String value,
            @Nonnull String text,
            @Nonnull String descr) {
        final String classChain = String.format(
                "**/%s[`%s == \"%s\"`]",
                type.xcuiElement,
                attribute.toString().toLowerCase(),
                value);
        return new IOSUIElement(classChain, text, descr);
    }

    public static IOSUIElement byPlainNameAndLabel(
            @Nonnull Type type,
            @Nonnull String name,
            @Nonnull String label,
            @Nonnull String descr) {
        final String classChain = String.format(
                "**/%s[`name == \"%s\" AND label == \"%s\"`]",
                type.xcuiElement,
                name,
                label);
        return new IOSUIElement(classChain, label, descr);
    }

    public static IOSUIElement byName(@Nonnull String name) {
        return byName(name, "");
    }

    public static IOSUIElement byName(
            @Nonnull String name,
            @Nonnull String descr) {
        return byName(name, "", descr);
    }

    public static IOSUIElement byName(
            @Nonnull String name,
            @Nonnull String label,
            @Nonnull String descr) {
        return byName(ANY, name, label, descr);
    }

    public static IOSUIElement byName(
            @Nonnull Type type,
            @Nonnull String name,
            @Nonnull String label,
            @Nonnull String descr) {
        return byAttribute(type, NAME, name, label, descr);
    }

    public static IOSUIElement byNameAndLabel(
            @Nonnull Type type,
            @Nonnull String name,
            @Nonnull String label,
            @Nonnull String descr) {
        final String classChain = String.format(
                "**/%s[`name == \"%s\" AND label == \"%s\" AND visible == 1`]",
                type.xcuiElement,
                name,
                label);
        return new IOSUIElement(classChain, label, descr);
    }

    public static IOSUIElement byXpath(
            @Nonnull String xpath,
            @Nonnull String text,
            @Nonnull String descr) {
        return new IOSUIElement(By.xpath(xpath), text, descr);
    }

    public static IOSUIElement byXpath(
            @Nonnull String xpath,
            @Nonnull String descr) {
        return byXpath(xpath, "", descr);
    }

    public static IOSUIElement byPredicate(
            @Nonnull String predicate,
            @Nonnull String label,
            @Nonnull String descr) {
        return new IOSUIElement(AppiumBy.iOSNsPredicateString(predicate), label, descr);
    }

    public static IOSUIElement textFieldByName(
            @Nonnull String name,
            @Nonnull String label,
            @Nonnull String descr) {
        return byName(TEXT_FIELD, name, label, descr);
    }

    public static IOSUIElement staticTextByName(
            @Nonnull String name,
            @Nonnull String descr) {
        return byName(STATIC_TEXT, name, name, descr);
    }

    public static IOSUIElement staticTextByName(
            @Nonnull String name,
            @Nonnull String label,
            @Nonnull String descr) {
        return byName(STATIC_TEXT, name, label, descr);
    }

    public static IOSUIElement textViewByLabel(
            @Nonnull String label,
            @Nonnull String descr) {
        return byAttribute(TEXT_VIEW, LABEL, label, label, descr);
    }

    public static IOSUIElement linkByLabel(@Nonnull String label) {
        return byAttribute(LINK, LABEL, label, label, String.format("'%s' link", label));
    }

    public static IOSUIElement buttonByLabel(@Nonnull String label) {
        return buttonByLabel(label, String.format("'%s' button", label));
    }

    public static IOSUIElement buttonByLabel(
            @Nonnull String label,
            @Nonnull String descr) {
        return byAttribute(BUTTON, LABEL, label, label, descr);
    }

    public static IOSUIElement buttonByName(@Nonnull String name) {
        return buttonByName(name, String.format("'%s' button", name));
    }

    public static IOSUIElement buttonByName(@Nonnull String name, @Nonnull String descr) {
        return buttonByName(name, "", descr);
    }

    public static IOSUIElement buttonByName(@Nonnull String name, @Nonnull String label, @Nonnull String descr) {
        return byName(BUTTON, name, label, descr);
    }

    public static IOSUIElement buttonByNameAndLabel(@Nonnull String name, @Nonnull String label, @Nullable String descr) {
        String description = descr != null ? descr : String.format("'%s' button", label);
        return byNameAndLabel(BUTTON, name, label, description);
    }

    public static IOSUIElement byClassChain(
            @Nonnull String classChain,
            @Nonnull String descr) {
        return new IOSUIElement(classChain, "", descr);
    }
}
