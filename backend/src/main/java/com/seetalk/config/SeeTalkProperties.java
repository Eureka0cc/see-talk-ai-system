package com.seetalk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seetalk")
public class SeeTalkProperties {

    private String corsOrigins = "http://localhost:5173,http://127.0.0.1:5173";

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
