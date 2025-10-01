FROM gradle:8.8-jdk17 AS build
COPY --chown=gradle:gradle ./ /home/gradle/project
WORKDIR /home/gradle/project
RUN gradle build -x test --no-daemon

FROM openjdk:17-jdk-slim
COPY --from=build /home/gradle/project/build/libs/msa-ticket-1.0.0.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=k8s

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=${SPRING_PROFILES_ACTIVE}"]