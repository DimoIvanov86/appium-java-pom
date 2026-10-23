package core.automation.selfhealing;

import bl.mobile.common.BaseUIElement;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.automation.AbstractDriverWrapper;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static tools.config.Config.HEALING_MODEL_DEFAULT;
import static tools.config.Config.HEALING_MODEL_PROPERTY;

/**
 * Repairs broken id/accessibility-id locators through the Claude API. Off unless selfHealing is
 * enabled for the run and ANTHROPIC_API_KEY is set. XPath is never healed: it encodes structure
 * a replacement could not keep.
 */
public class LocatorHealer {

    private static final Logger LOG = LoggerFactory.getLogger(LocatorHealer.class);
    public static final String API_KEY_ENV = "ANTHROPIC_API_KEY";
    private static final String MODEL = System.getProperty(HEALING_MODEL_PROPERTY, HEALING_MODEL_DEFAULT);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String REPORT_FILE = "test-output/self-healing-report.html";
    private static final int MAX_PAGE_SOURCE_LENGTH = 100000;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final String ANDROID_KEEP_PATTERN = "resource-id|content-desc|text";
    private static final String IOS_KEEP_PATTERN = "name|label|value|type";
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static volatile String apiKey;
    private static final HealingCache cache = new HealingCache();
    private static final List<HealingRecord> healingLog = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger consecutiveApiFailures = new AtomicInteger(0);
    private static final AtomicBoolean circuitBreakerLogged = new AtomicBoolean(false);
    private static final Set<String> unfixable = ConcurrentHashMap.newKeySet();
    private static final Object REPORT_LOCK = new Object();

    public static void initIfEnabled(boolean enabled) {
        if (!enabled) {
            return;
        }
        String key = System.getenv(API_KEY_ENV);
        if (key == null || key.isEmpty()) {
            LOG.warn("Self-healing is enabled, but {} is not set - healing stays off", API_KEY_ENV);
            return;
        }
        apiKey = key;
        cache.loadFromFile();
        LOG.info("Self-healing locators on, model {}", MODEL);
    }

    public static @Nullable By getHealedBy(@Nonnull BaseUIElement uiEl) {
        HealingCache.CachedLocator cached = cache.get(uiEl.by.toString());
        return cached != null ? cached.toBy() : null;
    }

    /**
     * Finds the element with a healed locator (cached, else from the API).
     *
     * @throws NoSuchElementException the original exception, if the element can't be healed
     */
    public static @Nonnull WebElement tryHeal(
            @Nonnull AbstractDriverWrapper driver,
            @Nullable WebElement parent,
            @Nonnull BaseUIElement uiEl,
            @Nonnull NoSuchElementException originalException) {
        if (!canHeal(uiEl)) {
            throw originalException;
        }
        WebElement found;
        try {
            found = findWithHealedLocator(driver, parent, uiEl);
        } catch (RuntimeException e) {
            // a failure inside healing must not replace the real "element not found"
            LOG.warn("Self-healing of {} failed", uiEl.description, e);
            found = null;
        }
        if (found == null) {
            unfixable.add(uiEl.by.toString());
            throw originalException;
        }
        return found;
    }

    private static @Nullable WebElement findWithHealedLocator(
            @Nonnull AbstractDriverWrapper driver,
            @Nullable WebElement parent,
            @Nonnull BaseUIElement uiEl) {
        String originalLocator = uiEl.by.toString();
        By cachedBy = getHealedBy(uiEl);
        if (cachedBy != null) {
            WebElement found = findOrNull(driver, parent, cachedBy);
            if (found != null) {
                driver.log.healed(uiEl.description, originalLocator, cachedBy.toString());
                return found;
            }
            driver.log.debug("[SelfHealing] Cached locator failed, removing it from the cache");
            cache.remove(originalLocator);
        }

        Suggestion suggestion = askForLocator(driver, uiEl);
        WebElement found = suggestion == null ? null : findOrNull(driver, parent, suggestion.by);
        if (found != null) {
            remember(uiEl, suggestion);
            driver.log.healed(uiEl.description, originalLocator, suggestion.by.toString());
        }
        return found;
    }

    // For findElements: returns a healed locator (cached, else from the API) that finds at least one
    // element, or null.
    public static @Nullable By tryHealLocator(
            @Nonnull AbstractDriverWrapper driver,
            @Nullable WebElement parent,
            @Nonnull BaseUIElement uiEl) {
        if (!canHeal(uiEl)) {
            return null;
        }
        By cachedBy = getHealedBy(uiEl);
        if (cachedBy != null) {
            return cachedBy;
        }
        Suggestion suggestion;
        try {
            suggestion = askForLocator(driver, uiEl);
            if (suggestion != null && findOrNull(driver, parent, suggestion.by) == null) {
                suggestion = null;
            }
        } catch (RuntimeException e) {
            LOG.warn("Self-healing of {} failed", uiEl.description, e);
            suggestion = null;
        }
        if (suggestion == null) {
            unfixable.add(uiEl.by.toString());
            return null;
        }
        remember(uiEl, suggestion);
        return suggestion.by;
    }

    private static boolean canHeal(@Nonnull BaseUIElement uiEl) {
        String locator = uiEl.by.toString();
        return apiKey != null
                && !locator.startsWith("By.xpath")
                && !unfixable.contains(locator);
    }

    // A locator the API proposed; cached and reported only once it has found the element.
    private static final class Suggestion {
        final HealingCache.CachedLocator locator;
        final By by;
        final String confidence;
        final String reasoning;

        Suggestion(HealingCache.CachedLocator locator, String confidence, String reasoning) {
            this.locator = locator;
            this.by = locator.toBy();
            this.confidence = confidence;
            this.reasoning = reasoning;
        }
    }

    private static void remember(@Nonnull BaseUIElement uiEl, @Nonnull Suggestion suggestion) {
        String originalLocator = uiEl.by.toString();
        cache.put(originalLocator, suggestion.locator);
        healingLog.add(new HealingRecord(
                uiEl.description, originalLocator, suggestion.by.toString(), suggestion.confidence, suggestion.reasoning));
    }

    // Asks the API for a new locator. Null if the API has no usable, different one.
    private static @Nullable Suggestion askForLocator(@Nonnull AbstractDriverWrapper driver, @Nonnull BaseUIElement uiEl) {
        String pageSource = getStrippedPageSource(driver);
        if (pageSource == null) {
            return null;
        }
        driver.log.debug("[SelfHealing] Asking the API for a locator of " + uiEl.description);
        String answer = sendToApi(buildPrompt(uiEl, pageSource, getPlatform(driver)));
        JsonObject result = answer == null ? null : parseAnswer(answer);
        if (result == null) {
            return null;
        }

        String originalLocator = uiEl.by.toString();
        HealingCache.CachedLocator locator = new HealingCache.CachedLocator(
                stringOrNull(result, "locatorType"),
                stringOrNull(result, "locatorValue"));
        if (!locator.isValid()) {
            LOG.warn("Self-healing answer for {} has no usable locator: {}", uiEl.description, result);
            return null;
        }
        // the same locator back means it isn't broken - the element is simply not there
        if (locator.toBy().toString().equals(originalLocator)) {
            return null;
        }
        return new Suggestion(
                locator,
                stringOrDefault(result, "confidence", "unknown"),
                stringOrDefault(result, "reasoning", ""));
    }

    private static @Nonnull String buildPrompt(
            @Nonnull BaseUIElement uiEl,
            @Nonnull String pageSource,
            @Nonnull String platform) {
        return "You are a mobile test automation locator expert. A UI element locator stopped working.\n"
                + "Find the matching element in the page source and return a new locator.\n\n"
                + "Element description: \"" + uiEl.description + "\"\n"
                + "Expected text: \"" + uiEl.textByDesign + "\"\n"
                + "Failed locator: " + uiEl.by + "\n"
                + "Platform: " + platform + "\n\n"
                + "RULES:\n"
                + "- Never return the same locator as the failed one\n"
                + "- Never hardcode dynamic values (counts, numbers, dates) in locators\n"
                + "- Never use overly generic ids like 'title', 'subtitle', 'text', 'button'\n"
                + "- If you cannot find a reliable match, return {\"found\":false}\n\n"
                + "Page source (XML):\n" + pageSource + "\n\n"
                + "Respond ONLY with JSON, no other text:\n"
                + "{\"found\":true/false, \"locatorType\":\"id|accessibilityId|iOSClassChain|xpath\","
                + " \"locatorValue\":\"...\", \"confidence\":\"high|medium|low\", \"reasoning\":\"...\"}";
    }

    // Returns the model's text answer, or null on any failure. Stops calling after repeated failures.
    private static @Nullable String sendToApi(@Nonnull String prompt) {
        if (consecutiveApiFailures.get() >= MAX_CONSECUTIVE_FAILURES) {
            if (circuitBreakerLogged.compareAndSet(false, true)) {
                LOG.warn("Self-healing API disabled after {} consecutive failures", MAX_CONSECUTIVE_FAILURES);
            }
            return null;
        }

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        JsonArray messages = new JsonArray();
        messages.add(userMessage);
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", MODEL);
        requestBody.addProperty("max_tokens", 1024);
        requestBody.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .build();
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                consecutiveApiFailures.incrementAndGet();
                LOG.warn("Self-healing API returned status {}: {}", response.statusCode(), response.body());
                return null;
            }
            consecutiveApiFailures.set(0);
            JsonObject responseJson = GSON.fromJson(response.body(), JsonObject.class);
            return responseJson.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
        } catch (IOException | RuntimeException e) {
            consecutiveApiFailures.incrementAndGet();
            LOG.warn("Self-healing API call failed", e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // Returns the answer's JSON, or null if the model found no match or the answer isn't valid JSON.
    private static @Nullable JsonObject parseAnswer(@Nonnull String answer) {
        String json = answer.trim();
        // the model sometimes wraps the JSON in a markdown fence
        if (json.startsWith("```")) {
            json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        }
        try {
            JsonObject result = GSON.fromJson(json, JsonObject.class);
            boolean found = result != null && result.has("found") && result.get("found").getAsBoolean();
            return found ? result : null;
        } catch (RuntimeException e) {
            LOG.warn("Self-healing answer is not the expected JSON: {}", answer);
            return null;
        }
    }

    private static @Nullable String stringOrNull(@Nonnull JsonObject json, @Nonnull String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : null;
    }

    private static @Nonnull String stringOrDefault(@Nonnull JsonObject json, @Nonnull String key, @Nonnull String fallback) {
        String value = stringOrNull(json, key);
        return value == null ? fallback : value;
    }

    private static @Nullable WebElement findOrNull(
            @Nonnull AbstractDriverWrapper driver,
            @Nullable WebElement parent,
            @Nonnull By by) {
        try {
            return parent != null ? parent.findElement(by) : driver.driver.findElement(by);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    private static String getPlatform(@Nonnull AbstractDriverWrapper driver) {
        return driver.getClass().getSimpleName().contains("IOS") ? "IOS" : "ANDROID";
    }

    private static @Nullable String getStrippedPageSource(@Nonnull AbstractDriverWrapper driver) {
        try {
            String pageSource = stripPageSource(driver.getPageSource(), getPlatform(driver));
            if (pageSource.length() > MAX_PAGE_SOURCE_LENGTH) {
                driver.log.debug("[SelfHealing] Page source truncated to " + MAX_PAGE_SOURCE_LENGTH);
                pageSource = pageSource.substring(0, MAX_PAGE_SOURCE_LENGTH);
            }
            return pageSource;
        } catch (RuntimeException e) {
            LOG.warn("Self-healing could not read the page source", e);
            return null;
        }
    }

    private static String stripPageSource(String pageSource, String platform) {
        String keepPattern = "ANDROID".equals(platform) ? ANDROID_KEEP_PATTERN : IOS_KEEP_PATTERN;
        return pageSource.replaceAll(" (?!" + keepPattern + ")\\w+(?:-\\w+)*=\"[^\"]*\"", "");
    }

    public static void printHealingSummary() {
        Collection<HealingRecord> healed = uniqueHealings();
        if (healed.isEmpty()) {
            return;
        }
        StringBuilder summary = new StringBuilder("Self-healed locators (" + healed.size() + "):");
        for (HealingRecord r : healed) {
            summary.append(String.format("%n    %-30s | was: %-30s | now: %-30s | confidence: %s",
                    r.elementDescription, r.originalLocator, r.healedLocator, r.confidence));
        }
        LOG.info(summary.toString());
    }

    public static void generateReport() {
        Collection<HealingRecord> healed = uniqueHealings();
        if (healed.isEmpty()) {
            return;
        }

        StringBuilder html = new StringBuilder();
        html.append("<html><head><title>Self-Healing Report</title>")
                .append("<style>")
                .append("body{font-family:sans-serif} ")
                .append("table{border-collapse:collapse;width:100%} ")
                .append("th,td{border:1px solid #009;padding:8px;text-align:left} ")
                .append("th{background:#E6EBF9} ")
                .append("tr:nth-child(even){background:#f9f9f9}")
                .append("</style></head><body>")
                .append("<h2>Self-Healing Locators Report</h2>")
                .append("<p>").append(healed.size()).append(" locator(s) were healed during this run.</p>")
                .append("<table>")
                .append("<tr><th>Element</th><th>Original Locator</th>")
                .append("<th>Healed Locator</th><th>Confidence</th><th>Reasoning</th></tr>");
        for (HealingRecord r : healed) {
            html.append("<tr>")
                    .append("<td>").append(r.elementDescription).append("</td>")
                    .append("<td><code>").append(r.originalLocator).append("</code></td>")
                    .append("<td><code>").append(r.healedLocator).append("</code></td>")
                    .append("<td>").append(r.confidence).append("</td>")
                    .append("<td>").append(r.reasoning).append("</td>")
                    .append("</tr>");
        }
        html.append("</table></body></html>");

        synchronized (REPORT_LOCK) {
            try (FileWriter writer = new FileWriter(REPORT_FILE)) {
                writer.write(html.toString());
            } catch (IOException e) {
                LOG.warn("Could not write {}", REPORT_FILE, e);
            }
        }
    }

    // the same locator can be healed in several tests; report it once
    private static Collection<HealingRecord> uniqueHealings() {
        Map<String, HealingRecord> unique = new LinkedHashMap<>();
        synchronized (healingLog) {
            for (HealingRecord r : healingLog) {
                unique.putIfAbsent(r.originalLocator, r);
            }
        }
        return unique.values();
    }
}
