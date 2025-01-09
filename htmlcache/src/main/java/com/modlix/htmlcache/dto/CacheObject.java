package com.modlix.htmlcache.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@ToString
public class CacheObject implements Serializable {

    @Serial
    private static final long serialVersionUID = 1l;

    private long createdAt = System.currentTimeMillis();
    private String url;
    private String pathKey;
    @NonNull
    private String appCode;
    @NonNull
    private String clientCode;
    private String html;

    public static String getFileName(String pathKey, String appCode, String clientCode) {
        return appCode + "-" + clientCode + "-" + pathKey + ".cached";
    }

    public String getFileName() {
        return getFileName(pathKey, appCode, clientCode);
    }
}
