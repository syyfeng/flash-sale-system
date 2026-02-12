# 📊 System Flowcharts

## 1. High-Concurrency Order Creation

This flow handles the initial user request. The goal is to return a response as fast as possible while preventing overselling, offloading the heavy DB write to a background thread.

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant Nginx as Edge Nginx
    participant Gateway as Spring Gateway
    participant Order as Order Service
    participant Caffeine as JVM Cache
    participant Redis
    participant DB as MySQL (Order DB)
    participant Kafka

    Note over User, Gateway: Layer 1: Traffic Shaping
    User->>Nginx: POST /order/create
    Nginx->>Nginx: Check IP Rate Limit
    Nginx->>Gateway: Forward Request
    Gateway->>Gateway: Global Rate Limiter
    Gateway->>Order: Route to Service

    Note over Order, Caffeine: Layer 2: JVM Protection
    Order->>Caffeine: Check "isSoldOut"
    alt isSoldOut == true
        Order-->>User: 400 Sold Out (Fast Fail)
    end

    Note over Order, Redis: Layer 3: Atomic Pre-deduction
    Order->>Redis: EVAL Lua Script (DECR stock)
    alt Redis Stock < 0
        Redis-->>Order: Result: -1
        Order->>Caffeine: Set isSoldOut = true
        Order-->>User: 400 Sold Out
    else Stock > 0
        Redis-->>Order: Result: Success
        
        Note over Order, DB: Layer 4: Transactional Outbox (ACID)
        rect rgb(240, 248, 255)
            note right of Order: Start DB Transaction
            Order->>DB: INSERT Order (Status: PENDING)
            Order->>DB: INSERT Stock_Log (Status: LOCKED)
            Order->>DB: INSERT Local_Message (Status: NEW)
            Order->>DB: COMMIT Transaction
        end
        
        Order-->>User: 200 Success: Order Queued
        
        %% Async Phase
        par After Commit Hook
            Order->>Kafka: Send "ORDER_CREATED"
            Order->>DB: Update Local_Message (SENT)
        end
    end
```

---

## 2. Asynchronous Inventory Sync (The Consumer)

This process runs in the background in the `Inventory Service`. It ensures the MySQL inventory eventually matches the Redis inventory.

```mermaid
flowchart TD
    A[Kafka: 'order-create'] -->|Consume| B(Inventory Service)
    B --> C{Check Stock_Log?}
    C -->|Order Already Logged| D[Idempotency Check: Ignore]
    C -->|New Order| E[Start Transaction]
    
    E --> F[UPDATE inventory_stock SET stock = stock - 1]
    E --> G[UPDATE stock_log SET status = 'DEDUCTED']
    
    F --> H{DB Update Success?}
    H -->|Yes| I[Commit Transaction]
    H -->|No/Error| J[Retry / Alert]
```

---

## 3. Payment Flow & Compensation (Rollback)

This flow handles the user interaction *after* the order is created. It demonstrates the **Saga Pattern** (Compensation) where a failure triggers a reverse operation.

```mermaid
sequenceDiagram
    participant User
    participant Payment as Payment Service
    participant Order as Order Service
    participant DB as MySQL (Order DB)
    participant Inventory as Inventory Service
    participant Redis
    participant InvDB as MySQL (Inventory DB)

    User->>Payment: Click "Pay" or "Cancel"
    
    alt Payment Success
        Payment->>Order: API: /pay/{id} (Success)
        Order->>DB: Update Order Status = PAID
        Order->>Inventory: Notify: Payment Success
        Inventory->>InvDB: Update Stock_Log = FINALIZED
        
    else Payment Fail / Cancel
        Payment->>Order: API: /cancel/{id}
        Order->>DB: Update Order Status = CANCELLED
        
        rect rgb(255, 230, 230)
            Note over Order, Redis: 🚨 COMPENSATION (Stock Rollback)
            Order->>Inventory: Notify: Rollback Stock
            
            %% 1. Restore Redis (Must do)
            Inventory->>Redis: INCR Stock (Restore Cache)
            
            %% 2. Restore DB (Conditional)
            Inventory->>InvDB: Check Stock_Log Status
            alt Status == DEDUCTED
                Inventory->>InvDB: UPDATE Inventory Stock +1
                Inventory->>InvDB: UPDATE Stock_Log = ROLLED_BACK
            else Status == LOCKED
                Inventory->>InvDB: UPDATE Stock_Log = ROLLED_BACK
                Note right of Inventory: Stock wasn't deducted yet, no need to add +1
            end
        end
        
        Order-->>User: Refund / Cancelled
    end

```

---

## 4. Local Message Retry (Reliability)

A scheduled task ensures that even if the Kafka send fails (network glitch), the message is eventually sent.

```mermaid
stateDiagram-v2
    direction LR

    state "Phase 1: Immediate Send" as S1 {
        [*] --> NEW: DB Commit
        NEW --> ATTEMPT_1: Transaction Hook
        ATTEMPT_1 --> WAIT_RETRY: Network Error
    }

    state "Phase 2: Scheduled Retry" as S2 {
        WAIT_RETRY --> SCAN_DB: Cron Job (Every 1m)
        SCAN_DB --> ATTEMPT_RETRY
        ATTEMPT_RETRY --> RETRY_CHECK: Fail
        RETRY_CHECK --> WAIT_RETRY: Count < 3
    }

    %% --- Final States (Outside Boxes) ---
    state "Success (Message Delivered)" as SENT
    state "Dead Letter (Manual Intervention)" as DEAD_LETTER

    %% --- Exit Transitions ---
    ATTEMPT_1 --> SENT: Success
    ATTEMPT_RETRY --> SENT: Success
    RETRY_CHECK --> DEAD_LETTER: Count >= 3
```