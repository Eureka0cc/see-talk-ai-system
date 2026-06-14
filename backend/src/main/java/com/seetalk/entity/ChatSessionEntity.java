package com.seetalk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Comment("聊天会话表")
@Entity
@Table(name = "chat_session")
public class ChatSessionEntity extends BaseEntity {

    @Comment("会话标题（AI 生成或首条用户消息摘要）")
    @Column(length = 128)
    private String title;

    @Comment("最近一条 AI 回复摘要，用于历史列表预览")
    @Column(name = "last_message_preview", length = 200)
    private String lastMessagePreview;

    @Comment("最后活跃时间")
    @Column(name = "last_active_time", nullable = false)
    private LocalDateTime lastActiveTime;

    @Comment("消息条数")
    @Column(name = "message_count", nullable = false)
    private Integer messageCount = 0;

    @Embedded
    private AuditFields audit = new AuditFields();

    @Override
    protected AuditFields audit() {
        return audit;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLastMessagePreview() {
        return lastMessagePreview;
    }

    public void setLastMessagePreview(String lastMessagePreview) {
        this.lastMessagePreview = lastMessagePreview;
    }

    public LocalDateTime getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(LocalDateTime lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    public Integer getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(Integer messageCount) {
        this.messageCount = messageCount;
    }
}
