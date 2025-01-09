package com.modlix.htmlcache.service;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.google.common.hash.Hashing;
import com.modlix.htmlcache.dto.CacheObject;
import com.modlix.htmlcache.enumeration.Environment;
import com.modlix.htmlcache.exception.HtmlCacheException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class HtmlCacheService {

    public static final Logger logger = LoggerFactory.getLogger(HtmlCacheService.class);

    private final ExecutorService virtualThreadExecutor;

    private final Map<Environment, Cache> caches = new HashMap<>();

    @Value("${fileCachePath:/tmp/htmlcache}")
    private String fileCachePath;

    @Value("${chromedriver:/usr/bin/chromedriver}")
    private String chromeDriver;

    public HtmlCacheService(CacheManager cacheManager) {

        Stream.of(Environment.values()).forEach(e -> caches.put(e, cacheManager.getCache(e + "_HTML_CACHE")));

        this.virtualThreadExecutor = new ThreadPoolExecutor(
                10,
                10,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                Thread.ofVirtual().factory());
    }

    @PostConstruct
    public void initialize() {

        System.setProperty("webdriver.chrome.driver", chromeDriver);
        Stream.of(Environment.values()).forEach(e -> {
            try {
                Files.createDirectories(Paths.get(this.fileCachePath, e.toString()));
            } catch (IOException ex) {
                logger.error("Unable to create cache directory {} : {}", e, this.fileCachePath);
                throw new HtmlCacheException("Unable to create cache directory for " + e + " : " + this.fileCachePath,
                        ex);
            }
        });
    }

    @PreDestroy
    public void shutdownExecutor() {
        System.out.println("Shutting down executor...");
        virtualThreadExecutor.shutdown();
        try {
            if (!virtualThreadExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.error("Executor did not terminate in the specified time.");
                virtualThreadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Shutdown interrupted.", e);
            virtualThreadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public String get(Environment env, String urlKey, HttpServletRequest request) {

        String appCode = request.getParameter("appCode");
        String clientCode = request.getParameter("clientCode");

        if (!StringUtils.hasText(clientCode) || !StringUtils.hasText(appCode)) {
            throw new HtmlCacheException("Client Code and Application Code parameters cannot be empty");
        }

        String url = validateAndGetURL(request, urlKey);

        String pathKey = Hashing.sha256().hashBytes(url.getBytes()).toString();

        CacheObject cached = this.caches.get(env).get(pathKey, CacheObject.class);

        if (cached == null) {
            CacheObject newCached = new CacheObject(appCode, clientCode)
                    .setPathKey(pathKey)
                    .setUrl(url);

            try (ObjectInputStream ois = new ObjectInputStream(
                    Files.newInputStream(Paths.get(this.fileCachePath, env.toString(), newCached.getFileName())))) {
                cached = (CacheObject) ois.readObject();
            } catch (IOException | ClassNotFoundException ex) {
                cached = newCached;
                logger.info("No file cache is available for {} : {}", env, cached);
            }
        }

        if (cached.getHtml() != null)
            return cached.getHtml();

        final CacheObject finalCached = cached;
        this.virtualThreadExecutor.submit(() -> {

            String waitTimeString = request.getParameter("waitTime");
            Long waitTime = 0l;
            if (StringUtils.hasText(waitTimeString)) {
                try {
                    waitTime = Long.parseLong(waitTimeString);
                } catch (NumberFormatException ex) {
                    logger.info("Unable to parse the wait time request parameter : {}", waitTimeString);
                }
            }
            this.getHTML(env, finalCached, waitTime);
        });

        return "";
    }

    private void getHTML(Environment env, CacheObject co, long waitTime) {

        CacheObject cached = this.caches.get(env).get(co.getPathKey(), CacheObject.class);
        if (cached != null && cached.getCreatedAt() > co.getCreatedAt())
            return;

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--disable-gpu", "--window-size=1280,1024");

        WebDriver driver = new ChromeDriver(options);

        String url = co.getUrl();
        try {

            driver.get(url);

            if (waitTime > 0)
                Thread.sleep(Duration.ofMillis(waitTime).toMillis());

            List<WebElement> list = driver.findElements(By.tagName("html"));
            if (list == null || list.isEmpty())
                return;

            co.setCreatedAt(System.currentTimeMillis());
            this.caches.get(env).put(co.getPathKey(), co.setHtml(list.get(0).getAttribute("outerHTML")));

            Thread.ofVirtual().start(() -> {
                try (ObjectOutputStream oos = new ObjectOutputStream(
                        Files.newOutputStream(Paths.get(this.fileCachePath, env.toString(), co.getFileName()),
                                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                    oos.writeObject(co);
                } catch (Exception ex) {
                    logger.error("Unable to write the file : {}", co.getFileName());
                }
            });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Thread was interrupted while taking screenshot of URL: {}", url);
            throw new HtmlCacheException("Thread was interrupted while taking screenshot of URL: " + url, ex);
        } catch (Exception ex) {

            throw new HtmlCacheException("Unable to take screenshot of URL: " + url, ex);
        } finally {
            driver.quit();
        }
    }

    private static String validateAndGetURL(HttpServletRequest request, String key) {
        String url = request.getRequestURI().trim();

        if (url == null || url.isBlank()) {
            logger.error("URL is missing");
            throw new HtmlCacheException("URL is missing");
        }

        if (key != null && !key.isBlank()) {
            int index = url.indexOf(key);
            if (index == -1) {
                logger.error("Key is missing in URL: {}", key);
                throw new HtmlCacheException("Key is missing in URL: " + key);
            }
            url = url.substring(index + key.length()).trim();
        }

        url = url.replace("//", "/");

        if (url.startsWith("/"))
            url = url.substring(1);

        if (url.startsWith("https/"))
            url = "https://" + url.substring(6);
        else if (url.startsWith("http/"))
            url = "http://" + url.substring(5);
        else
            url = "https://" + url;

        try {
            new URI(url).toURL();
        } catch (MalformedURLException | URISyntaxException e) {
            logger.error("Invalid URL: {}", url);
            throw new HtmlCacheException("Invalid URL: " + url, e);
        }

        return url;
    }

    public void invalidateAllCache(Environment env) {

        try {
            Files.walk(Paths.get(this.fileCachePath, env.toString()))
                    .filter(e -> e.getFileName().toString().toLowerCase().endsWith(".cached"))
                    .forEach(e -> {
                        try {
                            Files.delete(e);
                        } catch (Exception ex) {
                            logger.error("Unable to delete all caches of {} : {}", env, e, ex);
                        }
                    });
        } catch (Exception ex) {
            logger.error("Unable to walk the path {} : {}", env, this.fileCachePath, ex);
        }

        this.caches.get(env).clear();
    }

    public void invalidateAllCache(Environment env, String appCode, String clientCode) {

        boolean hasAppCode = StringUtils.hasText(appCode);
        boolean hasClientCode = StringUtils.hasText(clientCode);

        try {
            Files.walk(Paths.get(this.fileCachePath, env.toString()))
                    .filter(e -> fileNamePredicate(e.getFileName().toString(), appCode, clientCode, hasAppCode,
                            hasClientCode))
                    .forEach(e -> {
                        try {
                            Files.delete(e);
                        } catch (Exception ex) {
                            logger.error("Unable to delete : {}", e, ex);
                        }
                        String pathKey = e.getFileName().toString();
                        pathKey = pathKey.substring(pathKey.lastIndexOf('-') + 1);
                        this.caches.get(env).evict(pathKey);
                    });
        } catch (Exception ex) {
            logger.error("Unable to walk the path {} : {}", env, this.fileCachePath, ex);
        }
    }

    private boolean fileNamePredicate(String fileName, String appCode, String clientCode, boolean hasAppCode,
            boolean hasClientCode) {

        if (!fileName.endsWith(".cached"))
            return false;

        if (hasAppCode && !fileName.startsWith(appCode))
            return false;

        if (hasClientCode) {
            int indexOf = fileName.indexOf('-');
            if (indexOf == -1)
                return false;
            indexOf++;
            int endIndexOf = fileName.indexOf('-', indexOf);
            if (endIndexOf == -1)
                return false;
            if (!clientCode.equals(fileName.substring(indexOf, endIndexOf)))
                return false;
        }

        return true;
    }

    public void invalidateCache(Environment env, String urlKey, HttpServletRequest request) {

        String appCode = request.getParameter("appCode");
        String clientCode = request.getParameter("clientCode");

        if (!StringUtils.hasText(clientCode) || !StringUtils.hasText(appCode)) {
            throw new HtmlCacheException("Client Code and Application Code parameters cannot be empty");
        }

        String url = validateAndGetURL(request, urlKey);

        String pathKey = Hashing.sha256().hashBytes(url.getBytes()).toString();

        CacheObject cached = this.caches.get(env).get(pathKey, CacheObject.class);

        if (cached != null) {
            this.caches.get(env).evict(pathKey);
        }

        String fileName = CacheObject.getFileName(pathKey, appCode, clientCode);

        try {
            Files.deleteIfExists(Paths.get(this.fileCachePath, env.toString(), fileName));
        } catch (Exception ex) {
            logger.error("Unable to delete {} : {}", env, fileName, ex);
        }
    }
}