package com.potd.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads configuration from config.properties.
 *
 * <p>Resolution order for any key (highest priority first):
 * <ol>
 *   <li>Environment variable with GFG_ prefix (e.g. {@code GFG_USERNAME} overrides key {@code username})</li>
 *   <li>Java system property  (-Dusername=... on the command line)</li>
 *   <li>config.properties file</li>
 * </ol>
 *
 * <p>The GFG_ prefix avoids collisions with OS-level variables such as the
 * Windows built-in {@code USERNAME}, {@code TEMP}, or {@code PATH}.
 */
public class ConfigReader {

    private static final Properties properties = new Properties();

    static {
        try (InputStream input = ConfigReader.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new RuntimeException("config.properties not found in classpath");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }
    }

    private ConfigReader() {}

    /**
     * Returns the resolved value for {@code key}, checking env vars and system
     * properties before falling back to config.properties.
     */
    public static String getProperty(String key) {
        // 1. Environment variable — prefixed with GFG_ to avoid collisions with
        //    OS-level variables (e.g. Windows USERNAME, TEMP, PATH).
        //    "username" → GFG_USERNAME,  "copilot.api.token" → GFG_COPILOT_API_TOKEN
        String envKey = "GFG_" + key.toUpperCase().replace(".", "_");
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        // 2. JVM system property (-Dkey=value)
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp;
        }

        // 3. config.properties
        return properties.getProperty(key);
    }

    public static String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
