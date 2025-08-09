# Use an official OpenJDK runtime as a parent image
FROM openjdk:17-jdk-slim

# Set the timezone argument (default to Asia/Kolkata)
ARG TIMEZONE=Asia/Kolkata
ENV TZ=$TIMEZONE

# Configure timezone
RUN apt-get update && \
    apt-get install -y tzdata && \
    ln -sf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone && \
    apt-get clean

WORKDIR /app

COPY target/jtradebot-advance-processor-0.0.1-SNAPSHOT.jar /app/jtradebot-advance-processor.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/jtradebot-advance-processor.jar"]