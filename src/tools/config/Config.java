package tools.config;

/**
 * Names and defaults of the run settings. Gradle passes every -Ppom.* property to the test JVM as
 * a system property of the same name. App settings live in a config file: the one given with
 * -Ppom.env, else the suite's "config" parameter, else DEFAULT_CONFIG_PATH.
 */
public final class Config {

    public static final String DEFAULT_CONFIG_PATH = "config/config.properties";

    public static final String ADB_PORT_PROPERTY = "pom.adbPort";
    public static final String ADB_PORT_DEFAULT = "5037";
    public static final String APPIUM_PORT_PROPERTY = "pom.appiumPort";
    public static final String APPIUM_PORT_DEFAULT = "4723";

    public static final String WDA_CONNECTION_TIMEOUT_PROPERTY = "pom.wdaConnectionTimeout";
    public static final String WDA_CONNECTION_TIMEOUT_DEFAULT = "180000";
    // both empty by default: only real iOS devices need WebDriverAgent signing
    public static final String XCODE_ORG_ID_PROPERTY = "pom.xcodeOrgId";
    public static final String XCODE_SIGNING_ID_PROPERTY = "pom.xcodeSigningId";

    public static final String HEALING_MODEL_PROPERTY = "pom.healingModel";
    public static final String HEALING_MODEL_DEFAULT = "claude-sonnet-5-5";

    public static final String STACK_TRACE_PACKAGES_PROPERTY = "pom.stackTracePackages";
    public static final String STACK_TRACE_PACKAGES_DEFAULT = "bl.,core.,tests.,tools.";

    private Config() {
    }
}
