FROM eclipse-temurin:25-jre

LABEL org.opencontainers.image.source="https://github.com/Guslarz/invoice-watcher"

COPY mail-producer-app-@PROJECT_VERSION@.jar /usr/bin/app/app.jar
WORKDIR /usr/bin/app

CMD java -jar app.jar
