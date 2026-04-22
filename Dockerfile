FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# JAR is pre-built by ./scripts/mvn-local.sh package (run before docker build in CI)
COPY target/trawhile-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
