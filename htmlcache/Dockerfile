FROM mcr.microsoft.com/playwright:v1.49.1-noble

RUN apt-get update && apt-get install -y \
    openjdk-21-jdk \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

COPY target/htmlcache-1.0.0.jar htmlcache-1.0.0.jar

ENV INSTANCE_ID=default

EXPOSE 8080

VOLUME [ "/logs", "/tmp/htmlcache" ]

ENTRYPOINT ["java", "-Dlogging.file.name=/logs/htmlcache-${INSTANCE_ID}.log", "-jar", "htmlcache-1.0.0.jar"]