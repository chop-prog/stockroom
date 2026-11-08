package com.chopprog.stockroom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiryJob.class);
    private final StockService stock;

    ExpiryJob(StockService stock) {
        this.stock = stock;
    }

    @Scheduled(fixedDelayString = "${stockroom.expiry-interval}")
    void run() {
        int expired = stock.expireDue();
        if (expired > 0) {
            log.info("Returned stock from {} expired reservations", expired);
        }
    }
}
