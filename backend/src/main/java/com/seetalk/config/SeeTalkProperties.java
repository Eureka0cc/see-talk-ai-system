package com.seetalk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seetalk")
public class SeeTalkProperties {

    private String corsOrigins = "http://localhost:5173,http://127.0.0.1:5173";
    private int maxImageWidth = 640;
    private int maxImageHeight = 480;
    private int maxFramesPerMinute = 12;
    private int maxContextMessages = 20;
    private int sessionTimeoutSeconds = 3600;
    private long defaultUserId = 329767336584859648L;
    private long snowflakeWorkerId = 1;
    private long snowflakeDatacenterId = 1;

    public int getMaxImageWidth() {
        return maxImageWidth;
    }

    public void setMaxImageWidth(int maxImageWidth) {
        this.maxImageWidth = maxImageWidth;
    }

    public int getMaxImageHeight() {
        return maxImageHeight;
    }

    public void setMaxImageHeight(int maxImageHeight) {
        this.maxImageHeight = maxImageHeight;
    }

    public int getMaxFramesPerMinute() {
        return maxFramesPerMinute;
    }

    public void setMaxFramesPerMinute(int maxFramesPerMinute) {
        this.maxFramesPerMinute = maxFramesPerMinute;
    }

    public int getMaxContextMessages() {
        return maxContextMessages;
    }

    public void setMaxContextMessages(int maxContextMessages) {
        this.maxContextMessages = maxContextMessages;
    }

    public int getSessionTimeoutSeconds() {
        return sessionTimeoutSeconds;
    }

    public void setSessionTimeoutSeconds(int sessionTimeoutSeconds) {
        this.sessionTimeoutSeconds = sessionTimeoutSeconds;
    }

    public long getDefaultUserId() {
        return defaultUserId;
    }

    public void setDefaultUserId(long defaultUserId) {
        this.defaultUserId = defaultUserId;
    }

    public String getCorsOrigins() {
        return corsOrigins;
    }

    public void setCorsOrigins(String corsOrigins) {
        this.corsOrigins = corsOrigins;
    }

    public String[] getCorsOriginArray() {
        return corsOrigins.split(",");
    }

    public long getSnowflakeWorkerId() {
        return snowflakeWorkerId;
    }

    public void setSnowflakeWorkerId(long snowflakeWorkerId) {
        this.snowflakeWorkerId = snowflakeWorkerId;
    }

    public long getSnowflakeDatacenterId() {
        return snowflakeDatacenterId;
    }

    public void setSnowflakeDatacenterId(long snowflakeDatacenterId) {
        this.snowflakeDatacenterId = snowflakeDatacenterId;
    }
}
