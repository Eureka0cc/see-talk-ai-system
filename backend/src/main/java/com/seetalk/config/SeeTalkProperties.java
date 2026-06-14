package com.seetalk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seetalk")
public class SeeTalkProperties {

    private String corsOrigins = "http://localhost:5173,http://127.0.0.1:5173";
    private int maxImageWidth = 640;
    private int maxImageHeight = 480;
    private int maxFramesPerMinute = 12;

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

    public String getCorsOrigins() {
        return corsOrigins;
    }

    public void setCorsOrigins(String corsOrigins) {
        this.corsOrigins = corsOrigins;
    }

    public String[] getCorsOriginArray() {
        return corsOrigins.split(",");
    }
}
