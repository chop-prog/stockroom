package com.chopprog.stockroom;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByStatusAndExpiresAtLessThanEqual(Reservation.Status status, Instant moment);

    /**
     * Each way out of ACTIVE is a conditional update. When confirm, release and expiry race,
     * exactly one of them changes the row, so stock is returned at most once.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Reservation r set r.status = com.chopprog.stockroom.Reservation.Status.CONFIRMED"
            + " where r.id = :id and r.status = com.chopprog.stockroom.Reservation.Status.ACTIVE and r.expiresAt > :now")
    int confirm(@Param("id") long id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("update Reservation r set r.status = :to"
            + " where r.id = :id and r.status = com.chopprog.stockroom.Reservation.Status.ACTIVE")
    int close(@Param("id") long id, @Param("to") Reservation.Status to);
}
