package com.modlix.htmlcache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@AutoConfiguration
@EnableCaching
@SpringBootApplication
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
public class HtmlCacheApplication {

	public static void main(String[] args) {
		SpringApplication.run(HtmlCacheApplication.class, args);
	}

}
