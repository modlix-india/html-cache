package com.modlix.htmlcache.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.modlix.htmlcache.enumeration.Environment;
import com.modlix.htmlcache.service.HtmlCacheService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/html")
public class HtmlCacheController {

    public static final Logger logger = LoggerFactory.getLogger(HtmlCacheController.class);

    private final HtmlCacheService htmlCacheService;

    public HtmlCacheController(HtmlCacheService htmlCacheService) {
        this.htmlCacheService = htmlCacheService;
    }

    @GetMapping("/**")
    public ResponseEntity<String> get(@RequestParam Environment env, HttpServletRequest request) {
        return ResponseEntity.ok(this.htmlCacheService.get(env, "html", request));
    }

    @DeleteMapping("/**")
    public ResponseEntity<Boolean> invalidateURL(@RequestParam Environment env, HttpServletRequest request) {
        this.htmlCacheService.invalidateCache(env, "html", request);
        return ResponseEntity.ok(true);
    }

    @DeleteMapping("/all")
    public ResponseEntity<Boolean> invalidateAll(@RequestParam Environment env,
            @RequestParam(name = "appCode", required = false) String appCode,
            @RequestParam(name = "clientCode", required = false) String clientCode) {
        if (appCode == null && clientCode == null) {
            this.htmlCacheService.invalidateAllCache(env);
        } else {
            this.htmlCacheService.invalidateAllCache(env, appCode, clientCode);
        }

        return ResponseEntity.ok(true);
    }
}
