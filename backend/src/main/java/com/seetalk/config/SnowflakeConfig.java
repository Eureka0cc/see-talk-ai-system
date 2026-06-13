package com.seetalk.config;

import com.seetalk.id.SnowflakeIdWorker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SnowflakeConfig {

    @Bean
    public SnowflakeIdWorker snowflakeIdWorker(SeeTalkProperties properties) {
        return new SnowflakeIdWorker(
                properties.getSnowflakeWorkerId(),
                properties.getSnowflakeDatacenterId());
    }
}
