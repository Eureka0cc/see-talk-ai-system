package com.seetalk.repository;

import com.seetalk.entity.ChatSessionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, Long> {

    Page<ChatSessionEntity> findAllByOrderByLastActiveTimeDesc(Pageable pageable);
}
