package com.seetalk.config;

import com.seetalk.model.constants.AsyncConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "chatTaskExecutor")
    public Executor chatTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(AsyncConstants.CORE_POOL_SIZE);
        executor.setMaxPoolSize(AsyncConstants.MAX_POOL_SIZE);
        executor.setQueueCapacity(AsyncConstants.QUEUE_CAPACITY);
        executor.setThreadNamePrefix(AsyncConstants.THREAD_NAME_PREFIX);
        executor.initialize();
        return executor;
    }
}
