package core.automation.selfhealing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Healed locators, persisted so the next run starts from them instead of calling the API again.
public class HealingCache {

    private static final Logger LOG = LoggerFactory.getLogger(HealingCache.class);

    private static final String CACHE_FILE = "test-output/self-healing-cache.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Object lock = new Object();
    private final HashMap<String, CachedLocator> cache = new HashMap<>();

    public static class CachedLocator {
        private static final List<String> SUPPORTED_TYPES = List.of("id", "xpath", "accessibilityId", "iOSClassChain");

        public final String locatorType;
        public final String locatorValue;

        public CachedLocator(String locatorType, String locatorValue) {
            this.locatorType = locatorType;
            this.locatorValue = locatorValue;
        }

        // false for what toBy() can't build: an edited cache file or an unexpected API answer
        public boolean isValid() {
            return SUPPORTED_TYPES.contains(locatorType) && locatorValue != null && !locatorValue.isEmpty();
        }

        public @Nonnull By toBy() {
            switch (locatorType) {
                case "id":
                    return By.id(locatorValue);
                case "xpath":
                    return By.xpath(locatorValue);
                case "accessibilityId":
                    return AppiumBy.accessibilityId(locatorValue);
                case "iOSClassChain":
                    return AppiumBy.iOSClassChain(locatorValue);
                default:
                    throw new IllegalArgumentException("Unknown locator type: " + locatorType);
            }
        }
    }

    public @Nullable CachedLocator get(@Nonnull String key) {
        synchronized (lock) {
            CachedLocator locator = cache.get(key);
            if (locator != null && !locator.isValid()) {
                LOG.warn("Dropping the invalid cached locator for {}", key);
                cache.remove(key);
                return null;
            }
            return locator;
        }
    }

    public void put(@Nonnull String key, @Nonnull CachedLocator value) {
        synchronized (lock) {
            cache.put(key, value);
            saveToFile();
        }
    }

    public void remove(@Nonnull String key) {
        synchronized (lock) {
            cache.remove(key);
            saveToFile();
        }
    }

    public void loadFromFile() {
        synchronized (lock) {
            try (FileReader reader = new FileReader(CACHE_FILE)) {
                Type type = new TypeToken<Map<String, CachedLocator>>() {}.getType();
                Map<String, CachedLocator> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    loaded.values().removeIf(locator -> locator == null || !locator.isValid());
                    cache.putAll(loaded);
                }
            } catch (FileNotFoundException e) {
                // first run: no cache yet
            } catch (IOException | RuntimeException e) {
                LOG.warn("Ignoring the unreadable cache file {}", CACHE_FILE, e);
            }
        }
    }

    private void saveToFile() {
        File file = new File(CACHE_FILE);
        file.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(cache, writer);
        } catch (IOException e) {
            LOG.warn("Could not save the self-healing cache to {}", CACHE_FILE, e);
        }
    }
}
