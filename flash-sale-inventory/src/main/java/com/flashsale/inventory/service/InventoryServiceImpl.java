package com.flashsale.inventory.service;

import com.flashsale.common.grpc.DeductStockRequest;
import com.flashsale.common.grpc.DeductStockResponse;
import com.flashsale.common.grpc.InventoryServiceRPCGrpc;
import com.flashsale.inventory.repository.InventoryStockRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * gRPC service for stock pre-deduction using multi-level caching.
 *
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  MULTI-LEVEL CACHE ARCHITECTURE                                  │
 * │                                                                  │
 * │  Layer 1: JVM (Caffeine)  — sold-out flag, ~0.01ms               │
 * │  Layer 2: Redis (Lua)     — atomic stock DECR, ~1ms              │ 
 * │  Layer 3: MySQL (DB)      — source of truth, ~5ms                │
 * │                                                                  │
 * │  WHY REDIS BEFORE DB:                                            │
 * │  Redis handles 100k+ ops/sec on a single node. Under 10k+ QPS    │
 * │  flash-sale traffic, hitting MySQL directly would cause massive  │
 * │  row-lock contention on the stock column. Redis absorbs the      │
 * │  burst, and only successful reservations proceed to DB (via      │
 * │  Kafka async flow), reducing DB writes to actual order count.    │
 * │                                                                  │
 * │  WHY LUA SCRIPT (not DECR alone):                                │
 * │  A bare DECR can go negative. The Lua script atomically checks   │
 * │  stock >= quantity AND decrements in one round-trip, preventing  │
 * │  overselling at the Redis layer.                                 │
 * └──────────────────────────────────────────────────────────────────┘
 */
@GrpcService
public class InventoryServiceImpl extends InventoryServiceRPCGrpc.InventoryServiceRPCImplBase {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private InventoryStockRepository inventoryStockRepository;

    /**
     * JVM-level sold-out cache (Layer 1).
     * Key: productId, Value: true (sold out).
     * Only sold-out products are cached — unsold products are NOT stored,
     * saving memory. When stock is replenished (payment failure rollback),
     * the entry is invalidated.
     */
    private Cache<Long, Boolean> localSoldOutCache;

    @PostConstruct
    public void init() {
        localSoldOutCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
    }

    /**
     * Lua script for atomic Redis stock pre-deduction.
     *
     * Logic:
     *   1. Check if key exists (product may not be in Redis yet)
     *   2. Read current stock
     *   3. If stock >= requested quantity → DECRBY and return remaining
     *   4. Else → return -1 (insufficient stock)
     *
     * This entire sequence executes atomically in Redis (single-threaded),
     * so no race condition is possible between the check and the decrement.
     */
    private static final String LUA_DEDUCT_SCRIPT =
        "if (redis.call('exists', KEYS[1]) == 1) then " +
        "    local stock = tonumber(redis.call('get', KEYS[1])); " +
        "    local num = tonumber(ARGV[1]); " +
        "    if (stock >= num) then " +
        "        return redis.call('decrby', KEYS[1], num); " +
        "    end; " +
        "end; " +
        "return -1;";

    @Override
    public void deductStock(DeductStockRequest request, StreamObserver<DeductStockResponse> responseObserver) {
        Long productId = request.getProductId();
        int quantity = request.getQuantity();

        // ═══════════════════════════════════════════════════════════
        // LAYER 1: JVM Cache — fastest rejection path (~0.01ms)
        // If this product is already marked sold-out in local memory,
        // we don't even touch Redis. This protects Redis from being
        // overwhelmed when stock is already depleted.
        // ═══════════════════════════════════════════════════════════
        if (localSoldOutCache.getIfPresent(productId) != null) {
            log.debug("[Layer1-JVM] Product {} blocked by sold-out cache", productId);
            responseObserver.onNext(buildResponse(false, "Failed: Out of stock (JVM cache)"));
            responseObserver.onCompleted();
            return;
        }

        // ═══════════════════════════════════════════════════════════
        // LAYER 2: Redis Lua — atomic stock pre-deduction (~1ms)
        // ═══════════════════════════════════════════════════════════
        String redisKey = "product:stock:" + productId;
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(LUA_DEDUCT_SCRIPT, Long.class);
        Long result = redisTemplate.execute(redisScript, Collections.singletonList(redisKey), String.valueOf(quantity));

        boolean success = (result != null && result >= 0);

        if (!success) {
            // ═══════════════════════════════════════════════════════
            // LAYER 3 (fallback): DB verification
            // Redis says stock is depleted, but we verify against DB
            // once before permanently marking as sold-out. This handles
            // edge cases where Redis state diverges from DB (e.g., after
            // a Redis restart or a payment-failure rollback that only
            // restored DB stock but not Redis).
            // ═══════════════════════════════════════════════════════
            boolean dbHasStock = inventoryStockRepository
                    .findByProductId(productId)
                    .map(s -> s.getStock() > 0)
                    .orElse(false);

            if (dbHasStock) {
                log.warn("[Layer3-DB] Redis depleted but DB has stock for product {}. " +
                         "Possible Redis/DB drift. NOT marking as sold-out.", productId);
                // Don't mark sold-out; let subsequent requests retry Redis
                // (an admin or scheduled task should resync Redis from DB)
            } else {
                // Both Redis and DB confirm: truly sold out
                localSoldOutCache.put(productId, true);
                log.info("[Layer1-JVM] Product {} marked as sold-out in JVM cache", productId);
            }

            responseObserver.onNext(buildResponse(false, "Failed: Out of stock"));
            responseObserver.onCompleted();
            return;
        }

        // Stock pre-deducted in Redis successfully
        log.info("[Layer2-Redis] Product {} stock pre-deducted, remaining: {}", productId, result);
        responseObserver.onNext(buildResponse(true, "Success. Redis stock remaining: " + result));
        responseObserver.onCompleted();
    }

    /**
     * Invalidate the JVM sold-out cache for a product.
     * Called when stock is restored (e.g., payment failure rollback).
     */
    public void clearSoldOutFlag(Long productId) {
        localSoldOutCache.invalidate(productId);
        log.info("[Layer1-JVM] Sold-out flag cleared for product {}", productId);
    }

    private DeductStockResponse buildResponse(boolean success, String msg) {
        return DeductStockResponse.newBuilder()
                .setSuccess(success)
                .setMessage(msg)
                .build();
    }
}
