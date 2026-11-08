package com.chopprog.stockroom;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

@Service
public class StockService {

    private final ProductRepository products;
    private final ReservationRepository reservations;
    private final Clock clock;
    private final Duration ttl;

    public StockService(ProductRepository products, ReservationRepository reservations, Clock clock,
            @Value("${stockroom.reservation-ttl}") Duration ttl) {
        this.products = products;
        this.reservations = reservations;
        this.clock = clock;
        this.ttl = ttl;
    }

    public Product createProduct(String sku, String name, int quantity) {
        try {
            return products.saveAndFlush(new Product(sku, name, quantity));
        } catch (DataIntegrityViolationException e) {
            throw problem(HttpStatus.CONFLICT, "SKU already exists: " + sku);
        }
    }

    @Transactional(readOnly = true)
    public Product product(long id) {
        return products.findById(id).orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "No product " + id));
    }

    @Transactional
    public Product restock(long id, int quantity) {
        if (products.putBack(id, quantity) == 0) {
            throw problem(HttpStatus.NOT_FOUND, "No product " + id);
        }
        return product(id);
    }

    @Transactional
    public Reservation reserve(long productId, int quantity) {
        if (!products.existsById(productId)) {
            throw problem(HttpStatus.NOT_FOUND, "No product " + productId);
        }
        if (products.take(productId, quantity) == 0) {
            throw problem(HttpStatus.CONFLICT, "Not enough stock for product " + productId);
        }
        return reservations.save(new Reservation(productId, quantity, clock.instant().plus(ttl)));
    }

    @Transactional(readOnly = true)
    public Reservation reservation(long id) {
        return reservations.findById(id).orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "No reservation " + id));
    }

    @Transactional
    public Reservation confirm(long id) {
        if (reservations.confirm(id, clock.instant()) == 0) {
            throw notActive(id);
        }
        return reservation(id);
    }

    @Transactional
    public Reservation release(long id) {
        Reservation current = reservation(id);
        if (reservations.close(id, Reservation.Status.RELEASED) == 0) {
            throw notActive(id);
        }
        products.putBack(current.getProductId(), current.getQuantity());
        return reservation(id);
    }

    /** Returns stock from overdue reservations. Safe to run twice or alongside confirm and release. */
    @Transactional
    public int expireDue() {
        int expired = 0;
        var due = reservations.findByStatusAndExpiresAtLessThanEqual(Reservation.Status.ACTIVE, clock.instant());
        for (Reservation r : due) {
            if (reservations.close(r.getId(), Reservation.Status.EXPIRED) == 1) {
                products.putBack(r.getProductId(), r.getQuantity());
                expired++;
            }
        }
        return expired;
    }

    private ErrorResponseException notActive(long id) {
        Reservation current = reservation(id); // 404 if there is no such reservation at all
        return problem(HttpStatus.CONFLICT, "Reservation " + id + " is " + current.getStatus()
                + (current.getStatus() == Reservation.Status.ACTIVE ? " but has passed its deadline" : ""));
    }

    private static ErrorResponseException problem(HttpStatus status, String detail) {
        return new ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), null);
    }
}
