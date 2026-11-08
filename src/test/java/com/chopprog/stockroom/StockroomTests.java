package com.chopprog.stockroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.ErrorResponseException;

@SpringBootTest(properties = "stockroom.expiry-interval=PT1H") // tests run expiry themselves
@AutoConfigureMockMvc
@Import(StockroomTests.TestTime.class)
class StockroomTests {

    static class MovableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-11-09T12:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @TestConfiguration
    static class TestTime {
        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired StockService stock;
    @Autowired MovableClock clock;

    private Product product(int quantity) {
        return stock.createProduct("SKU-" + UUID.randomUUID(), "Coffee beans", quantity);
    }

    private int available(Product p) {
        return stock.product(p.getId()).getAvailable();
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void createsAndReadsProduct() throws Exception {
        postJson("/api/products", """
                {"sku": "beans-1", "name": "Beans", "quantity": 7}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.available").value(7));
    }

    @Test
    void duplicateSkuIsConflict() throws Exception {
        Product p = product(1);
        postJson("/api/products", """
                {"sku": "%s", "name": "Again", "quantity": 1}""".formatted(p.getSku()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void invalidBodyIsBadRequestProblem() throws Exception {
        postJson("/api/reservations", """
                {"productId": 1, "quantity": 0}""")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        postJson("/api/reservations", """
                {"productId": 999999, "quantity": 1}""")
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/products/999999")).andExpect(status().isNotFound());
    }

    @Test
    void reservingTakesStock() throws Exception {
        Product p = product(5);
        postJson("/api/reservations", """
                {"productId": %d, "quantity": 2}""".formatted(p.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(available(p)).isEqualTo(3);
    }

    @Test
    void notEnoughStockIsConflictAndChangesNothing() throws Exception {
        Product p = product(1);
        postJson("/api/reservations", """
                {"productId": %d, "quantity": 2}""".formatted(p.getId()))
                .andExpect(status().isConflict());
        assertThat(available(p)).isEqualTo(1);
    }

    @Test
    void restockAddsUnits() throws Exception {
        Product p = product(1);
        postJson("/api/products/%d/restock".formatted(p.getId()), """
                {"quantity": 4}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(5));
    }

    @Test
    void confirmKeepsStockTakenAndWorksOnce() throws Exception {
        Product p = product(3);
        long id = stock.reserve(p.getId(), 1).getId();
        mvc.perform(post("/api/reservations/%d/confirm".formatted(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(post("/api/reservations/%d/confirm".formatted(id))).andExpect(status().isConflict());
        assertThat(available(p)).isEqualTo(2);
    }

    @Test
    void releaseReturnsStockOnce() throws Exception {
        Product p = product(3);
        long id = stock.reserve(p.getId(), 2).getId();
        mvc.perform(delete("/api/reservations/%d".formatted(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
        mvc.perform(delete("/api/reservations/%d".formatted(id))).andExpect(status().isConflict());
        assertThat(available(p)).isEqualTo(3);
    }

    @Test
    void confirmedReservationCannotBeReleased() {
        Product p = product(1);
        long id = stock.reserve(p.getId(), 1).getId();
        stock.confirm(id);
        assertThatThrownBy(() -> stock.release(id)).isInstanceOf(ErrorResponseException.class);
        assertThat(available(p)).isZero();
    }

    @Test
    void overdueReservationCannotBeConfirmed() throws Exception {
        Product p = product(1);
        long id = stock.reserve(p.getId(), 1).getId();
        clock.advance(Duration.ofMinutes(15));
        mvc.perform(post("/api/reservations/%d/confirm".formatted(id))).andExpect(status().isConflict());
    }

    @Test
    void expiryReturnsStockExactlyOnce() {
        Product p = product(4);
        long id = stock.reserve(p.getId(), 3).getId();
        stock.expireDue(); // not due yet
        assertThat(stock.reservation(id).getStatus()).isEqualTo(Reservation.Status.ACTIVE);

        clock.advance(Duration.ofMinutes(16));
        assertThat(stock.expireDue()).isGreaterThanOrEqualTo(1);
        assertThat(stock.expireDue()).isZero();
        assertThat(stock.reservation(id).getStatus()).isEqualTo(Reservation.Status.EXPIRED);
        assertThat(available(p)).isEqualTo(4);
    }

    /** Runs the tasks at the same moment and returns how many finished without a domain error. */
    private <T> int race(List<Callable<T>> tasks) throws Exception {
        try (var pool = Executors.newFixedThreadPool(tasks.size())) {
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<Boolean>>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        task.call();
                        return true;
                    } catch (ErrorResponseException refused) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int ok = 0;
            for (var f : futures) {
                if (f.get()) {
                    ok++;
                }
            }
            return ok;
        }
    }

    @Test
    void hundredConcurrentRequestsCannotOversell() throws Exception {
        Product p = product(10);
        var tasks = new ArrayList<Callable<Reservation>>();
        for (int i = 0; i < 100; i++) {
            tasks.add(() -> stock.reserve(p.getId(), 1));
        }
        assertThat(race(tasks)).isEqualTo(10);
        assertThat(available(p)).isZero();
    }

    @Test
    void confirmAndReleaseRaceHasOneWinner() throws Exception {
        for (int round = 0; round < 25; round++) {
            Product p = product(1);
            long id = stock.reserve(p.getId(), 1).getId();
            int won = race(List.<Callable<Reservation>>of(() -> stock.confirm(id), () -> stock.release(id)));
            assertThat(won).isEqualTo(1);
            var status = stock.reservation(id).getStatus();
            assertThat(available(p)).isEqualTo(status == Reservation.Status.RELEASED ? 1 : 0);
        }
    }
}
