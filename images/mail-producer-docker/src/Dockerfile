FROM eclipse-temurin:25-jre

LABEL org.opencontainers.image.source="https://github.com/Guslarz/invoice-watcher"
LABEL org.opencontainers.image.title="Invoice Watcher - Mail Producer"
LABEL org.opencontainers.image.version="@PROJECT_VERSION@"
LABEL org.opencontainers.image.created="@DATE_NOW@"

COPY mail-producer-app-@PROJECT_VERSION@.jar /usr/bin/app/app.jar
WORKDIR /usr/bin/app

CMD java -jar app.jar
