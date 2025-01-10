# HTML Cache

This service will be used to take a copy of the webpage after it is loaded and cached. The cache is based on a multi level key one is app and the other is client codes.

## Usage

### How to retrieve the html

```
curl -s http://localhost:8080/html/<url>?appCode=<appCode>&clientCode=<clientCode>&<request parameters>
```

1. URL

   Examples

   ### Secured URLs

   http://localhost:8080/html/https/www.google.com

   ### Unsecured URLs

   http://localhost:8080/html/http/182.3.44.22/page/id/001

   ### Un specified protocol scheme defaults to https

   http://localhost:8080/html/www.mysite.com/page/id/123-34-22

   **Currently port number is not supported in the url**

1. appCode - Application Code

1. clientCode - Client Code

1. Request Parameters

   1. **waitTime** - Wait time determines how many milli seconds the thread needs to wait before caching the page html.

This service will often return empty string and caches in the background which is a time taking process. Any requests submitted before it finishes will be ignored.

### How to invalidate the html

```
curl -s -X DELETE http://localhost:8080/html/<url>?appCode=<appCode>&clientCode=<clientCode>
```

All the values are same as above but if client code is missing or null then all client's cache for that app code will be invalidated.

### How to invalidate all urls that belongs to an app code or/and client code

```
curl -s -X DELETE http://localhost:8080/html/all?appCode=<appCode>&clientCode=<clientCode>
```

### How to invalidate all cache

```
curl -s -X DELETE htpp://localhost:8080/html/all
```

## Configuration

1.  chromedriver

    Path to the installed chrome driver. Default is /usr/bin/chromedriver.

1.  fileCachePath

    Path to the file cache where all the images are stored. Defualt value is /tmp/htmlcache
