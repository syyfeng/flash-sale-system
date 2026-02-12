# ⚡ Enterprise Flash Sale System

A high-performance, distributed e-commerce system designed to handle **10k+ QPS** traffic spikes. It implements an **event-driven microservices architecture** with strict data consistency guarantees (Zero Overselling) using the **Transactional Outbox Pattern**, **Multi-Level Caching**, and **Optimistic Locking**.

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
* **Deployment:** Nginx (Serving static assets + Reverse Proxy).

### Infrastructure

* **Containerization:** Docker & Docker Compose (Full stack orchestration).
* **Service Discovery:** Netflix Eureka.
* **API Gateway:** Spring Cloud Gateway (with Rate Limiting).

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

## 📂 Project Structure

```text
flash-sale-system/
├── flash-sale-common      # Shared DTOs, gRPC Proto files, Utils
├── flash-sale-discovery   # Eureka Service Registry (Port: 8761)
├── flash-sale-gateway     # API Gateway & Rate Limiting (Port: 8080)
├── flash-sale-inventory   # Stock Management & Consumer (Port: 8081 / gRPC: 9090)
├── flash-sale-order       # Order Lifecycle, Outbox Producer (Port: 8082)
├── flash-sale-frontend    # React Client + Nginx (Port: 80)
├── docker-compose.yml     # Container orchestration
└── pom.xml                # Parent Maven configuration

```

---

## ⚡ Quick Start

### Prerequisites

* **Docker Desktop** (Running)
* **Java 17+** & **Maven** (For local compilation)

### One-Click Run

1. **Compile the Project:**
(This generates the JAR files required for the Docker build)
```bash
mvn clean package -DskipTests

```


2. **Start Services:**
```bash
docker compose up -d --build

```


3. **Access the System:**
* **Frontend (Mall & Admin):** [http://localhost](https://www.google.com/search?q=http://localhost)
* **Eureka Dashboard:** [http://localhost:8761](https://www.google.com/search?q=http://localhost:8761)
* **Kafka UI:** [http://localhost:8090](https://www.google.com/search?q=http://localhost:8090)


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
5. Check **Kafka UI** to see the message backlog processing.
