FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
# Temporary: legacy monolith tests must be migrated before the CI release gate.
RUN mvn -B -ntp -Dmaven.test.skip=true package \
    && cp target/audit-service-0.0.1-SNAPSHOT.jar /build/app.jar

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
COPY --from=build --chown=app:app /build/app.jar /app/app.jar
USER app
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
