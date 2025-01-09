package com.modlix.htmlcache.exception;

import java.io.Serial;

public class HtmlCacheException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1l;

    public HtmlCacheException(String message) {
        super(message);
    }

    public HtmlCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
