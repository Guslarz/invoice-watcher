FROM eclipse-temurin:25-jre

COPY mail-producer-app-@PROJECT_VERSION@.jar /usr/bin/app/app.jar
WORKDIR /usr/bin/app
EXPOSE 8080

CMD java -jar app.jar
