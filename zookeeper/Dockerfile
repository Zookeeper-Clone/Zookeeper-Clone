# Stage 1: Analyze dependencies and build custom JRE
FROM eclipse-temurin:17-jdk-alpine AS jre-builder

WORKDIR /app
COPY target/zookeeper-1.0-SNAPSHOT-shaded.jar app.jar

# Extract the JAR to analyze dependencies
RUN jar xf app.jar

# Find required Java modules
RUN apk add --no-cache binutils
RUN jdeps --ignore-missing-deps -q \
    --recursive \
    --multi-release 17 \
    --print-module-deps \
    --class-path 'BOOT-INF/lib/*' \
    app.jar > /deps.info

# Build minimal JRE with only required modules
RUN jlink \
    --verbose \
    --add-modules $(cat /deps.info),jdk.crypto.ec,jdk.crypto.cryptoki \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /javaruntime

# Stage 2: Create final image
FROM alpine:latest

# Install native dependencies required by RocksDB at RUNTIME
# FIX ADDED HERE: Added 'ca-certificates' and 'nss'
RUN apk add \
    libstdc++ \
    gcompat \
    libc6-compat \
    ca-certificates \
    nss

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=jre-builder /javaruntime $JAVA_HOME
COPY --from=jre-builder /app/app.jar /app/app.jar

EXPOSE 6000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]