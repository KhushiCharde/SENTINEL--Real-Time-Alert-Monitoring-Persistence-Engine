FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY *.java index.html ./
RUN mkdir -p build && javac --release 17 -d build AlertEngine.java Dashboard.java FileTailer.java HistoryStore.java LogEvent.java Main.java SampleLogWriter.java Severity.java WebServer.java && jar --create --file sentinel.jar --main-class sentinel.Main -C build . -C . index.html
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/sentinel.jar ./
COPY sqlite-jdbc-3.46.1.0.jar slf4j-api-2.0.13.jar slf4j-nop-2.0.13.jar ./
ENV PORT=8080 SENTINEL_DB=/data/sentinel-web.db
EXPOSE 8080
CMD ["java", "-XX:MaxRAMPercentage=70", "-cp", "sentinel.jar:*", "sentinel.WebServer"]
