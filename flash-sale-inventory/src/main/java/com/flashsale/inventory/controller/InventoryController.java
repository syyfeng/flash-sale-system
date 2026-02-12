package com.flashsale.inventory.controller;

import com.flashsale.inventory.entity.InventoryStock;
import com.flashsale.inventory.entity.Product;
import com.flashsale.inventory.repository.InventoryStockRepository;
import com.flashsale.inventory.repository.ProductRepository;
import com.flashsale.inventory.repository.StockLogRepository;
import com.flashsale.inventory.service.InventoryServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API for inventory management (admin + debug).
 * These endpoints are used by the Admin & Simulation Dashboard.
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryStockRepository inventoryStockRepository;

    @Autowired
    private StockLogRepository stockLogRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private InventoryServiceImpl inventoryService;

    // ─── Product CRUD ────────────────────────────────────────────

    /**
     * List all products with their stock info (DB stock + Redis stock).
     */
    @GetMapping("/products")
    public List<Map<String, Object>> listProducts() {
        List<Product> products = productRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Product p : products) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("name", p.getName());
            item.put("price", p.getPrice());

            // DB stock
            int dbStock = inventoryStockRepository.findByProductId(p.getId())
                    .map(InventoryStock::getStock)
                    .orElse(0);
            item.put("dbStock", dbStock);

            // Redis stock
            String redisKey = "product:stock:" + p.getId();
            String redisVal = redisTemplate.opsForValue().get(redisKey);
            int redisStock = redisVal != null ? Integer.parseInt(redisVal) : 0;
            item.put("redisStock", redisStock);

            result.add(item);
        }
        return result;
    }

    // ─── Create Product (INSERT new row) ──────────────────────────

    /**
     * Create a brand-new product. ID is auto-generated.
     *
     * Also initializes:
     *   - inventory_stock row with the given initial stock
     *   - Redis key product:stock:{id} for the multi-level cache
     *   - Clears any stale JVM sold-out flag
     *
     * @param body { name, price, stock }
     */
    @PostMapping("/products")
    public Map<String, Object> createProduct(@RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "New Product");
        Integer price = body.get("price") != null ? Integer.valueOf(body.get("price").toString()) : 0;
        Integer stock = body.get("stock") != null ? Integer.valueOf(body.get("stock").toString()) : 100;

        // INSERT new product (auto-generated ID)
        Product product = new Product();
        product.setName(name);
        product.setPrice(price);
        Product saved = productRepository.saveAndFlush(product);

        // Initialize inventory_stock
        InventoryStock invStock = new InventoryStock();
        invStock.setProductId(saved.getId());
        invStock.setStock(stock);
        inventoryStockRepository.save(invStock);

        // Initialize Redis stock
        String redisKey = "product:stock:" + saved.getId();
        redisTemplate.opsForValue().set(redisKey, String.valueOf(stock));

        // Clear sold-out flag (in case ID was reused after a reset)
        inventoryService.clearSoldOutFlag(saved.getId());

        log.info("[Admin] Product CREATED: id={}, name={}, price={}, stock={}",
                saved.getId(), name, price, stock);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("id", saved.getId());
        result.put("name", name);
        result.put("price", price);
        result.put("stock", stock);
        result.put("message", "Product created successfully");
        return result;
    }

    // ─── Update Product (MODIFY existing row) ─────────────────────

    /**
     * Update an existing product's details and/or stock.
     *
     * If stock is provided, the inventory_stock table AND Redis are
     * updated to stay consistent. JVM sold-out flag is also cleared.
     *
     * @param id   path variable — the product ID to update
     * @param body { name?, price?, stock? } — all fields are optional
     */
    @PutMapping("/products/{id}")
    public Map<String, Object> updateProduct(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Optional<Product> existingOpt = productRepository.findById(id);
        if (existingOpt.isEmpty()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("message", "Product not found: " + id);
            return err;
        }

        Product product = existingOpt.get();

        // Update product fields (only if provided)
        if (body.containsKey("name")) {
            product.setName((String) body.get("name"));
        }
        if (body.containsKey("price")) {
            product.setPrice(Integer.valueOf(body.get("price").toString()));
        }
        productRepository.save(product);

        // Update stock if provided
        if (body.containsKey("stock")) {
            Integer newStock = Integer.valueOf(body.get("stock").toString());

            InventoryStock invStock = inventoryStockRepository.findByProductId(id)
                    .orElseGet(() -> {
                        InventoryStock s = new InventoryStock();
                        s.setProductId(id);
                        return s;
                    });
            invStock.setStock(newStock);
            inventoryStockRepository.save(invStock);

            // Sync Redis
            String redisKey = "product:stock:" + id;
            redisTemplate.opsForValue().set(redisKey, String.valueOf(newStock));

            // Clear sold-out flag
            inventoryService.clearSoldOutFlag(id);
        }

        log.info("[Admin] Product UPDATED: id={}, body={}", id, body);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("id", id);
        result.put("name", product.getName());
        result.put("price", product.getPrice());
        result.put("message", "Product updated successfully");
        return result;
    }

    // ─── Stock Query ──────────────────────────────────────────────

    /**
     * Get real-time stock for a single product (for frontend polling).
     */
    @GetMapping("/stock/{productId}")
    public Map<String, Object> getStock(@PathVariable Long productId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productId", productId);

        // Redis stock (fast path)
        String redisKey = "product:stock:" + productId;
        String redisVal = redisTemplate.opsForValue().get(redisKey);
        result.put("redisStock", redisVal != null ? Integer.parseInt(redisVal) : 0);

        // DB stock
        int dbStock = inventoryStockRepository.findByProductId(productId)
                .map(InventoryStock::getStock)
                .orElse(0);
        result.put("dbStock", dbStock);

        return result;
    }

    // ─── System Reset (Debug) ────────────────────────────────────

    /**
     * Reset the entire system to initial state.
     * Used by the debug/stress panel to start fresh runs.
     */
    @PostMapping("/reset")
    public Map<String, String> resetSystem(@RequestBody(required = false) Map<String, Object> body) {
        int stockAmount = 100;
        if (body != null && body.get("stock") != null) {
            stockAmount = Integer.valueOf(body.get("stock").toString());
        }

        // 1. Truncate stock_log
        stockLogRepository.deleteAll();
        log.info("[Reset] stock_log truncated");

        // 2. Reset inventory_stock for all products
        List<Product> products = productRepository.findAll();
        for (Product p : products) {
            InventoryStock invStock = inventoryStockRepository.findByProductId(p.getId())
                    .orElse(new InventoryStock());
            invStock.setProductId(p.getId());
            invStock.setStock(stockAmount);
            inventoryStockRepository.save(invStock);

            // Reset Redis stock
            String redisKey = "product:stock:" + p.getId();
            redisTemplate.opsForValue().set(redisKey, String.valueOf(stockAmount));

            // Clear JVM sold-out flag
            inventoryService.clearSoldOutFlag(p.getId());
        }

        log.info("[Reset] All products stock reset to {}", stockAmount);

        Map<String, String> result = new LinkedHashMap<>();
        result.put("message", "Inventory reset complete. Stock set to " + stockAmount);
        return result;
    }
}
