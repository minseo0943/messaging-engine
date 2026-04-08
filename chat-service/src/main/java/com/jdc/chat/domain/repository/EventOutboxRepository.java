package com.jdc.chat.domain.repository;

import com.jdc.chat.domain.entity.EventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface EventOutboxRepository extends JpaRepository<EventOutbox, Long> {

    List<EventOutbox> findTop100ByPublishedFalseOrderByCreatedAtAsc();

    @Modifying
    @Query("UPDATE EventOutbox e SET e.published = true, e.publishedAt = :now WHERE e.id IN :ids")
    int markAsPublished(@Param("ids") List<Long> ids, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM EventOutbox e WHERE e.published = true AND e.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);

    long countByPublishedFalse();
}
