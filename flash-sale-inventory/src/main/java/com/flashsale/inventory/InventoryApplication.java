package com.flashsale.inventory;

import com.flashsale.inventory.entity.InventoryStock;
import com.flashsale.inventory.entity.Product;
import com.flashsale.inventory.repository.InventoryStockRepository;
import com.flashsale.inventory.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootApplication
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }

    /**
     * Initialize default product and stock on startup (idempotent).
     *
     * Uses findById + conditional insert to avoid merge conflicts
     * when the DB already contains data from a previous run.
     */
    @Bean
    public CommandLineRunner initData(
            StringRedisTemplate redisTemplate,
            ProductRepository productRepository,
            InventoryStockRepository inventoryStockRepository) {
        return args -> {
            // Initialize default product if not exists
            if (productRepository.findById(1L).isEmpty()) {
                Product p = new Product();
                p.setId(1L);
                p.setName("MacBook Pro M4 (Flash Sale)");
                p.setPrice(1999);
                productRepository.saveAndFlush(p);
                System.out.println("--- MySQL: products table initialized ---");
            } else {
                System.out.println("--- MySQL: products table already has product 1, skipping ---");
            }

            // Initialize inventory stock if not exists
            if (inventoryStockRepository.findByProductId(1L).isEmpty()) {
                InventoryStock stock = new InventoryStock();
                stock.setProductId(1L);
                stock.setStock(100);
                inventoryStockRepository.saveAndFlush(stock);
                System.out.println("--- MySQL: inventory_stock initialized ---");
            } else {
                System.out.println("--- MySQL: inventory_stock already has product 1, skipping ---");
            }

            // Redis stock initialization (always ensure it's set)
            String redisKey = "product:stock:1";
            String currentVal = redisTemplate.opsForValue().get(redisKey);
            if (currentVal == null) {
                // Read from DB to stay consistent
                int dbStock = inventoryStockRepository.findByProductId(1L)
                        .map(InventoryStock::getStock)
                        .orElse(100);
                redisTemplate.opsForValue().set(redisKey, String.valueOf(dbStock));
                System.out.println("--- Redis: product:stock:1 = " + dbStock + " ---");
            } else {
                System.out.println("--- Redis: product:stock:1 already set to " + currentVal + ", skipping ---");
            }
        };
    }
}
