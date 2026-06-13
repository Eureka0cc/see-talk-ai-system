package com.seetalk.entity;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@MappedSuperclass
@SQLRestriction("is_deleted = 0")
public abstract class BaseEntity implements Persistable<Long> {

    @Id
    @Comment("主键 ID（Snowflake 分布式 ID）")
    private Long id;

    protected abstract AuditFields audit();

    @Override
    public boolean isNew() {
        return getCreateTime() == null;
    }

    @PrePersist
    protected void onCreate() {
        audit().markCreated();
    }

    @PreUpdate
    protected void onUpdate() {
        audit().markUpdated();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getCreateTime() {
        return audit().getCreateTime();
    }

    public LocalDateTime getUpdateTime() {
        return audit().getUpdateTime();
    }

    public Boolean getDeleted() {
        return audit().getDeleted();
    }

    public void setDeleted(Boolean deleted) {
        audit().setDeleted(deleted);
    }
}
