package com.seetalk.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Embeddable
public class AuditFields {

    @Comment("创建时间")
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Comment("更新时间")
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @Comment("是否已删除（0=否，1=是）")
    @Column(name = "is_deleted", nullable = false)
    private Boolean deleted = false;

    public void markCreated() {
        LocalDateTime now = LocalDateTime.now();
        this.createTime = now;
        this.updateTime = now;
        if (this.deleted == null) {
            this.deleted = false;
        }
    }

    public void markUpdated() {
        this.updateTime = LocalDateTime.now();
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }
}
