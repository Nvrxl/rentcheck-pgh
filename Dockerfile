# How RentCheck is built and run on a hosting service (Render, DigitalOcean App Platform, ...).
# Two stages: the first has Maven and compiles the code, the second only has Java and runs it,
# which keeps the final image small.

# ---- stage 1: build one runnable jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B package -DskipTests

# ---- stage 2: run it ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/rentcheck.jar app.jar

# The hosting service tells us which port to listen on. App.java reads PORT (default 7070).
ENV PORT=8080
EXPOSE 8080

# MaxRAMPercentage keeps Java inside the container's memory limit on small free plans.
CMD ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
