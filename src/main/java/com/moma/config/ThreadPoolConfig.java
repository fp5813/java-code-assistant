package com.moma.config;

import com.moma.di.Bean;
import com.moma.di.Configuration;
import com.moma.concurrent.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 线程池配置。创建线程池管理器 Bean。
 * 对应 Spring {@code @Configuration}。
 */
@Configuration
public class ThreadPoolConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ThreadPoolConfig.class);

    @Bean
    public ThreadPoolManager threadPoolManager() {
        ThreadPoolManager manager = new ThreadPoolManager();
        LOG.info("线程池管理器 Bean 已创建 (@PostConstruct 将自动初始化)");
        return manager;
    }
}
