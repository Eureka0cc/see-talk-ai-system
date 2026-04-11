package com.seetalk.repository;

import com.seetalk.model.entity.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    @Query("""
            select m from ChatMessageEntity m
            where m.sessionId = :sessionId
            order by m.audit.createTime asc
            """)
    List<ChatMessageEntity> findBySessionIdOrderByAuditCreateTimeAsc(
            @Param("sessionId") Long sessionId);

    @Query("""
            select m from ChatMessageEntity m
            where m.sessionId = :sessionId
            order by m.audit.createTime desc
            """)
    List<ChatMessageEntity> findBySessionIdOrderByAuditCreateTimeDesc(
            @Param("sessionId") Long sessionId,
            Pageable pageable);

    @Query("""
            select m from ChatMessageEntity m
            where m.sessionId in (
                select s.id from ChatSessionEntity s
                where (s.userId = :userId or s.userId is null or s.userId = 0)
            )
            and (:startTime is null or m.audit.createTime >= :startTime)
            and (:endTime is null or m.audit.createTime < :endTime)
            and (:query is null or :query = '' or lower(m.content) like lower(concat('%', :query, '%')))
            order by m.audit.createTime desc
            """)
    List<ChatMessageEntity> searchVisibleMessagesByUserId(
            @Param("userId") Long userId,
            @Param("query") String query,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);
}
