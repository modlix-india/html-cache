package com.modlix.htmlcache.configuration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
public class HtmlCacheConfiguration {

    @SuppressWarnings("rawtypes")
    @Bean
    public Caffeine caffeineConfig() {
        return Caffeine.newBuilder().maximumSize(100);
    }

    @SuppressWarnings("rawtypes")
    @Bean
    public CacheManager cacheManager(Caffeine caffeine) {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(caffeine);
        return caffeineCacheManager;
    }
}