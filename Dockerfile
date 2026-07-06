# syntax=docker/dockerfile:1

# --- build stage ---
# 빌드 스테이지는 빌드 호스트 네이티브 아키텍처로 고정 — jar는 아키텍처 무관 바이트코드이므로
# Gradle 빌드는 1회만 수행하고, 런타임 스테이지만 타깃 플랫폼(amd64/arm64)별로 생성된다.
# (이 고정이 없으면 buildx 멀티아치 빌드 시 QEMU 에뮬레이션으로 Gradle이 돌아 배포당 15분+ 소요)
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew bootJar --no-daemon

# --- runtime stage ---
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
# 비-root 실행 (컨테이너 탈출 시 영향 범위 최소화)
RUN useradd -r -u 1001 appuser \
    && mkdir -p /app/uploads \
    && chown -R appuser:appuser /app
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]