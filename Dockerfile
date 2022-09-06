FROM openjdk:11-slim

WORKDIR /app

COPY target/pass-elide-test.jar .

ENTRYPOINT ["java","-jar","pass-elide-test.jar"]
