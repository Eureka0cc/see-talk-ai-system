package com.seetalk.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.SQLRestriction;

@Comment("聊天消息表")
@Entity
@SQLRestriction("is_deleted = 0")
@Table(name = "chat_message")
public class ChatMessageEntity extends BaseEntity {

    @Comment("所属会话 ID")
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Comment("消息角色（user / assistant）")
    @Column(nullable = false, length = 16)
    private String role;

    @Comment("消息内容")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Comment("是否使用视觉理解")
    @Column(name = "used_vision", nullable = false)
    private Boolean usedVision = false;

    @Embedded
    private AuditFields audit = new AuditFields();

    @Override
    protected AuditFields audit() {
        return audit;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean getUsedVision() {
        return usedVision;
    }

    public void setUsedVision(Boolean usedVision) {
        this.usedVision = usedVision;
    }
}
