package com.chopprog.stockroom;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class StockController {

    record NewProduct(@NotBlank @Size(max = 64) String sku, @NotBlank @Size(max = 200) String name,
            @Min(0) @Max(1_000_000) int quantity) {
    }

    record Restock(@Min(1) @Max(1_000_000) int quantity) {
    }

    record NewReservation(@NotNull Long productId, @Min(1) @Max(1_000) int quantity) {
    }

    record ProductView(long id, String sku, String name, int available) {
        static ProductView of(Product p) {
            return new ProductView(p.getId(), p.getSku(), p.getName(), p.getAvailable());
        }
    }

    record ReservationView(long id, long productId, int quantity, Reservation.Status status, Instant expiresAt) {
        static ReservationView of(Reservation r) {
            return new ReservationView(r.getId(), r.getProductId(), r.getQuantity(), r.getStatus(), r.getExpiresAt());
        }
    }

    private final StockService stock;

    StockController(StockService stock) {
        this.stock = stock;
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    ProductView createProduct(@Valid @RequestBody NewProduct body) {
        return ProductView.of(stock.createProduct(body.sku(), body.name(), body.quantity()));
    }

    @GetMapping("/products/{id}")
    ProductView product(@PathVariable long id) {
        return ProductView.of(stock.product(id));
    }

    @PostMapping("/products/{id}/restock")
    ProductView restock(@PathVariable long id, @Valid @RequestBody Restock body) {
        return ProductView.of(stock.restock(id, body.quantity()));
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    ReservationView reserve(@Valid @RequestBody NewReservation body) {
        return ReservationView.of(stock.reserve(body.productId(), body.quantity()));
    }

    @GetMapping("/reservations/{id}")
    ReservationView reservation(@PathVariable long id) {
        return ReservationView.of(stock.reservation(id));
    }

    @PostMapping("/reservations/{id}/confirm")
    ReservationView confirm(@PathVariable long id) {
        return ReservationView.of(stock.confirm(id));
    }

    @DeleteMapping("/reservations/{id}")
    ReservationView release(@PathVariable long id) {
        return ReservationView.of(stock.release(id));
    }
}
