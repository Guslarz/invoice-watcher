FROM eclipse-temurin:25-jre

COPY app.jar /usr/bin/app/app.jar
WORKDIR /usr/bin/app

ARG PROJECT_VERSION
ARG BUILD_DATE
LABEL org.opencontainers.image.source="https://github.com/Guslarz/invoice-watcher"
LABEL org.opencontainers.image.title="Invoice Watcher - Mail Producer"
LABEL org.opencontainers.image.version="${PROJECT_VERSION}"
LABEL org.opencontainers.image.created="${BUILD_DATE}"

CMD ["java", "-jar", "app.jar"]
