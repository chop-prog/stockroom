package com.chopprog.stockroom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    /** Units that can still be reserved. Changed only by the atomic queries in ProductRepository. */
    @Column(nullable = false)
    private int available;

    protected Product() {
    }

    public Product(String sku, String name, int available) {
        this.sku = sku;
        this.name = name;
        this.available = available;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public int getAvailable() {
        return available;
    }
}
