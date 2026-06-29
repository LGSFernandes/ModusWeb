package com.lfernandes.modusweb.configurations;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita o sistema de cache via anotações Spring.
 * A estratégia de cache (EhCache) é configurada em ehcache.xml.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
