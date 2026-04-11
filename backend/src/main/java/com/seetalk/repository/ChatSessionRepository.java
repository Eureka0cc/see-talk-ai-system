package com.seetalk.repository;

import com.seetalk.model.entity.ChatSessionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, Long> {

    @Query("""
            select s from ChatSessionEntity s
            where s.messageCount > 0
            and (s.userId = :userId or s.userId is null or s.userId = 0)
            order by s.lastActiveTime desc
            """)
    Page<ChatSessionEntity> findVisibleByUserIdOrderByLastActiveTimeDesc(
            @Param("userId") Long userId,
            Pageable pageable);

    @Query("""
            select s from ChatSessionEntity s
            where s.id = :id
            and (s.userId = :userId or s.userId is null or s.userId = 0)
            """)
    Optional<ChatSessionEntity> findVisibleByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId);

    @Query("""
            select count(s) > 0 from ChatSessionEntity s
            where s.id = :id
            and (s.userId = :userId or s.userId is null or s.userId = 0)
            """)
    boolean existsVisibleByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId);
}
