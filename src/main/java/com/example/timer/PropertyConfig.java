package com.example.timer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Optional;

/**
 * PropertyConfig: Loads configuration properties from the classpath
 * (e.g., from 'config.properties' or '[env]-config.properties').
 *
 * This utility mimics environment-specific property loading often found
 * in Mule applications, allowing the TimerManager to dynamically
 * determine the target endpoint URL and connection settings.
 */
public class PropertyConfig {

    private static final Properties PROPERTIES = new Properties();
    // Default file name used if no system property is set.
    private static final String DEFAULT_CONFIG_FILE = "config.properties";

    static {
        // Check for the 'mule.env' system property to determine the environment configuration file.
        // E.g., setting -Dmule.env=dev will load "dev-config.properties".
        String env = System.getProperty("mule.env");
        String configFileName;
        
        if (env != null && !env.trim().isEmpty()) {
            // Construct the environment-specific filename (e.g., "dev-config.properties")
            // Note: The structure assumes .properties files as per the usage of java.util.Properties
            configFileName = env.trim().toLowerCase() + "-config.properties";
        } else {
            configFileName = DEFAULT_CONFIG_FILE;
        }

        System.out.println("PropertyConfig: Attempting to load config file: " + configFileName);

        // Load the determined properties file
        try (InputStream input = PropertyConfig.class.getClassLoader().getResourceAsStream(configFileName)) {
            if (input == null) {
                System.err.println("FATAL: Could not find configuration file '" + configFileName + "' on the classpath.");
                // If config file is missing, the PROPERTIES map will remain empty,
                // and defaults will be used for endpoint parts (empty string) and timeouts (hardcoded).
            } else {
                PROPERTIES.load(input);
                System.out.println("PropertyConfig: Successfully loaded properties from " + configFileName);
            }
        } catch (IOException ex) {
            System.err.println("PropertyConfig: Error loading properties: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Gets a property value as a String.
     *
     * @param key The property key (e.g., "http.host").
     * @return The property value, or an empty string if not found.
     */
    public static String getProperty(String key) {
        // Get property, trimming to remove potential whitespace, and providing
        // an empty string as a default if the property is not found.
        return PROPERTIES.getProperty(key, "").trim();
    }
    
    /**
     * Gets a property value as an integer, using a default value if the property 
     * is missing or not a valid number.
     *
     * @param key The property key (e.g., "http.connectTimeout").
     * @param defaultValue The value to return if the property is missing or invalid.
     * @return The property value as an int.
     */
    public static int getIntProperty(String key, int defaultValue) {
        String value = getProperty(key);
        if (value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("Warning: Property '" + key + "' has an invalid format. Using default value " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * Dynamically builds the complete HTTP endpoint URL using the configured properties.
     * The format is: http://[host]:[port][basepath][path]
     *
     * It handles the optional basepath gracefully.
     *
     * @return The constructed full URL string.
     */
    public static String getMuleEndpointUrl() {
        String protocal = getProperty("http.protocal");
        String host = getProperty("http.host");
        String port = getProperty("http.port");
        String basepath = getProperty("http.basepath");
        String path = getProperty("http.path");

        // The exception message is updated to guide the user to check config file loading.
        if (host.isEmpty() || port.isEmpty() || path.isEmpty()) {
            throw new IllegalStateException("Missing required HTTP properties: host, port, or path. Check if your config file loaded correctly.");
        }

        // Handle basepath: only include it if it exists and is not empty
        String finalBasepath = Optional.ofNullable(basepath)
                .filter(s -> !s.isEmpty())
                .orElse(""); // defaults to empty string if missing

        // Build the URL string.
        String baseUrl = String.format("%s://%s:%s%s%s",protocal, host, port, finalBasepath, path);

        return baseUrl;
    }
}