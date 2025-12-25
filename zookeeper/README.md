# Zookeeper-Clone

This project is a simplified Zookeeper-like system implemented in Java using Raft.  
It includes a client library and a server implementation that can run as a cluster.

---

## Prerequisites

- Java 17 or higher
- Maven 3.9+ (for building the JAR)
- Docker & Docker Compose

---
# How to run the Zookeeper Cluster

## 1. Build the Fat JAR

A **fat JAR** includes all dependencies, **so** the Docker image can run without Maven.

```bash
# Navigate to the project directory
cd zookeeper

# Build the fat JAR using Maven
mvn clean package -DskipTests

# The JAR will be located in:
# target/zookeeper-1.0-SNAPSHOT-shaded.jar
```
## 2. Build the Docker Image
```bash
docker compose build
```
## 3. Run a cluster
```bash
docker compose up
```
or
```bash
docker compose up -d
```
If you want the cluster to run in the background

## Notes
make sure these files are in your zookeeper directory
```bash
Dockerfile
docker-compose.yml
```