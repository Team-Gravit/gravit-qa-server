FROM eclipse-temurin:21-jre

ARG JAR_NAME=gravit-qa-server-0.0.1-SNAPSHOT.jar
ARG JAR_PATH=./build/libs/${JAR_NAME}

WORKDIR /app

COPY ${JAR_PATH} /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
