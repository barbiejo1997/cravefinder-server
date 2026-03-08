# ============================================================
# WHAT IS THIS FILE?
# ============================================================
# A Dockerfile tells Render (or any server) exactly how to
# build and run your app inside a "container" — think of it
# like a self-contained box with everything your app needs.
#
# Each line is an instruction. They run top to bottom.
# ============================================================

# STAGE 1: BUILD
# Use an official Java 17 image that also has Maven included
# This downloads all dependencies and compiles your code
FROM maven:3.9.6-eclipse-temurin-17 AS build

# Set the working directory inside the container
WORKDIR /app

# Copy pom.xml first — Maven downloads dependencies based on this
COPY pom.xml .

# Download all dependencies (cached separately for faster rebuilds)
RUN mvn dependency:go-offline -B

# Copy the rest of the source code
COPY src ./src

# Build the project — creates target/cravefinder-server-1.0.0.jar
RUN mvn clean package -DskipTests

# ============================================================
# STAGE 2: RUN
# Use a smaller Java image just for running (no Maven needed)
# This keeps the final container lean and fast
# ============================================================
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy just the compiled JAR from the build stage
COPY --from=build /app/target/cravefinder-server-1.0.0.jar app.jar

# Tell Docker which port the app listens on
EXPOSE 8080

# The command that starts your server
ENTRYPOINT ["java", "-jar", "app.jar"]
