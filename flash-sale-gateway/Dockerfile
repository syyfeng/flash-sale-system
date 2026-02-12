# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy parent pom and all module poms first (Docker layer caching)
COPY pom.xml .
COPY flash-sale-common/pom.xml  flash-sale-common/pom.xml
COPY flash-sale-discovery/pom.xml flash-sale-discovery/pom.xml
COPY flash-sale-gateway/pom.xml  flash-sale-gateway/pom.xml
COPY flash-sale-inventory/pom.xml flash-sale-inventory/pom.xml
COPY flash-sale-order/pom.xml   flash-sale-order/pom.xml

# Copy source for this module (gateway has no dependency on flash-sale-common)
COPY flash-sale-gateway/ flash-sale-gateway/

# Build only this module and its reactor dependencies
RUN mvn package -pl flash-sale-gateway -am -DskipTests -B

# ---- Stage 2: Run ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/flash-sale-gateway/target/flash-sale-gateway-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
