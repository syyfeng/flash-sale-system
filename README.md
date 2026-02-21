# ⚡ Enterprise Flash Sale System

A high-performance, distributed e-commerce system designed to handle **10k+ QPS** traffic spikes. It implements an **event-driven microservices architecture** with strict data consistency guarantees (Zero Overselling) using the **Transactional Outbox Pattern**, **Multi-Level Caching**, and **Optimistic Locking**.

The system embraces modern cloud-native practices, featuring **automated CI/CD pipelines** via Jenkins and **container orchestration** via Kubernetes for zero-downtime rolling updates.

---

## 🏗 Architecture & Tech Stack

### Backend

* **Framework:** Java 17, Spring Boot 3.5, Spring Cloud (Gateway, Eureka).
* **Communication:** gRPC (Protobuf) for internal high-speed RPC; REST API for clients.
* **Database:** MySQL 8.0 (MyBatis-Plus / JPA).
* **Messaging:** Apache Kafka (Event-driven decoupling).
* **Cache:** Redis (Lettuce + Lua Scripts) + Caffeine (JVM Local Cache).

### Frontend

* **Stack:** React 18, TypeScript, Vite, Ant Design.
* **Deployment:** Nginx (Serving static assets + Reverse Proxy routing to API Gateway).

### Infrastructure & DevOps

* **Containerization:** Docker.
* **Orchestration:** Kubernetes (K8s) for managing microservice lifecycles, scaling, and self-healing.
* **CI/CD Pipeline:** Jenkins (Automated build, Docker image packaging, pushing to Docker Hub, and triggering K8s rolling updates).
* **Middleware Hosting:** Docker Compose (Used to host stateful services like MySQL, Redis, Kafka, Zookeeper locally).

---

## 🌟 Key Features

### 🚀 High Concurrency Strategy

1. **Layer 1 - Edge (Nginx/Gateway):** Token Bucket Rate Limiting to filter malicious traffic.
2. **Layer 2 - Application (JVM):** Caffeine Local Cache intercepts requests for "Sold Out" items, preventing network calls to Redis.
3. **Layer 3 - Distributed Cache (Redis):** Atomic Stock Pre-deduction using **Lua Scripts** to ensure thread safety without heavy DB locking.
4. **Layer 4 - Asynchronous DB Write:** Only successful Redis requests generate a Kafka message to update the Database eventually.

### 🛡 Data Consistency (The "Enterprise" Logic)

1. **Transactional Outbox Pattern:**
* Instead of sending to Kafka directly (which can fail), we write a message to a `local_message` table in the *same* DB transaction as the Order creation.
* This guarantees **Atomicity**: The Order exists IF AND ONLY IF the Message exists.


2. **Idempotency:**
* The Inventory Consumer checks a `stock_log` table before deducting stock to prevent duplicate processing.


3. **Compensation (Stock Rollback):**
* If Payment fails, times out, or is cancelled by the user, a compensation event triggers to restore stock in both Redis and MySQL.


---

## ⚡ Deployment & Quick Start

This project uses a hybrid deployment model for local development: **Middleware** runs on Docker Compose, while **Microservices** are orchestrated by Kubernetes and deployed via Jenkins.

### Prerequisites

* **Docker Desktop** (With Kubernetes enabled in settings)
* **Jenkins** (Running locally or on a server, connected to your Git repo)
* **Java 17+** & **Maven**

### Step 1: Start Stateful Middleware

Start the foundational databases and message brokers via Docker Compose. The K8s pods will connect to these via `host.docker.internal`.

```bash
docker compose up -d mysql redis zookeeper kafka

```

### Step 2: Automated CI/CD Deployment (Jenkins & K8s)

Instead of manually compiling and starting JAR files, we use Jenkins to automate the entire lifecycle.

1. Open your **Jenkins Dashboard**.
2. Trigger the `flash-sale-system` pipeline (**Build Now**).
3. The `Jenkinsfile` will automatically execute the following stages:
* **Compile:** Runs `mvn clean package`.
* **Build & Push Images:** Builds Docker images for all services and pushes them to Docker Hub.
* **Deploy to K8s:** Applies the manifests in the `k8s/` directory (`kubectl apply -f k8s/`).
* **Rolling Update:** Safely restarts K8s deployments (`kubectl rollout restart`) and waits for `readinessProbes` to pass, ensuring **zero-downtime deployments**.



### Step 3: Verify & Access the System

Check the status of your Kubernetes pods:

```bash
kubectl get pods

```

Wait until all pods show `Running` and `1/1` READY. Then access:

* **Frontend (Mall & Admin):** `http://localhost` (Served by Nginx)
* **Eureka Dashboard:** `http://localhost:8761`
* **Kafka UI:** `http://localhost:8090` (If configured in docker-compose)

---

## 📜 Database Design

The system uses specific tables to ensure high performance and consistency.

### 1. `inventory_stock`

Separated from the `product` table to reduce row locking contention during high concurrency.

### 2. `stock_log` (Idempotency)

Records every attempt to change stock. Used to prevent double-deduction if Kafka sends duplicate messages.

### 3. `local_message` (Transactional Outbox)

Guarantees message delivery to Kafka.

---

## 🧪 Testing Flow

### 1. Admin Setup

1. Go to **Admin Panel** -> **Seller Dashboard**.
2. Add a Product (e.g., Name: Airpods Pro, Stock: 10000).
3. Wait a moment for Redis to sync.

### 2. High Concurrency Stress Test

1. Go to **Admin Panel** -> **Debug Panel**.
2. Enter User Count: `1000`.
3. Click **Simulate Traffic**.
4. Observe **Redis Stock** drop instantly, while **DB Stock** drops eventually (Async sync).
5. Check **Kafka UI** to see the message backlog processing sequentially.
6. Observe Kubernetes Pods dynamically handling the load.

---
