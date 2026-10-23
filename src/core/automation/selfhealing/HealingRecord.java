package core.automation.selfhealing;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class HealingRecord {

    public final String elementDescription;
    public final String originalLocator;
    public final String healedLocator;
    public final String confidence;
    public final String reasoning;
    public final String timestamp;

    public HealingRecord(
            String elementDescription,
            String originalLocator,
            String healedLocator,
            String confidence,
            String reasoning) {
        this.elementDescription = elementDescription;
        this.originalLocator = originalLocator;
        this.healedLocator = healedLocator;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }
}
