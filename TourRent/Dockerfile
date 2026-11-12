FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /source
COPY pom.xml ./
COPY shared shared
COPY discovery-server discovery-server
COPY config-server config-server
COPY catalog-service catalog-service
COPY rental-service rental-service
COPY logistics-service logistics-service
COPY api-gateway api-gateway
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp verify

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache git curl && addgroup -S tourrent && adduser -S tourrent -G tourrent
WORKDIR /app
COPY config-repository /config-repository
RUN cd /config-repository && git init -b main && git config user.name "TourRent Config" && git config user.email "config@tourrent.local" && git add . && git commit -m "Initial application configuration" && chown -R tourrent:tourrent /app /config-repository
ARG MODULE
COPY --from=build --chown=tourrent:tourrent /source/${MODULE}/target/${MODULE}-1.0.0.jar /app/app.jar
USER tourrent
ENV JAVA_TOOL_OPTIONS="-Xms48m -Xmx160m -XX:MaxMetaspaceSize=160m -XX:ActiveProcessorCount=2"
ENTRYPOINT ["java","-jar","/app/app.jar"]
