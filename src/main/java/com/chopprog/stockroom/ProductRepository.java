package com.chopprog.stockroom;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Takes units only if enough remain. The check and the change are one SQL statement, so the
     * database serialises competing requests: reading first and saving later would oversell.
     *
     * @return 1 if the units were taken, 0 if there were not enough
     */
    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.available = p.available - :quantity where p.id = :id and p.available >= :quantity")
    int take(@Param("id") long id, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.available = p.available + :quantity where p.id = :id")
    int putBack(@Param("id") long id, @Param("quantity") int quantity);
}
