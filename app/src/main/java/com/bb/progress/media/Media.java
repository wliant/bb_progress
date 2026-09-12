package com.bb.progress.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An attachment on a care-log entry or a milestone achievement — exactly one of the two. */
@Entity
@Table(name = "media")
public class Media {

    @Id
    private UUID id;

    @Column(name = "care_log_id")
    private UUID careLogId;

    @Column(name = "milestone_id")
    private String milestoneId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaKind kind;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Media() {
    }

    private Media(UUID careLogId, String milestoneId, MediaKind kind, String objectKey,
            String contentType, int sortOrder) {
        this.id = UUID.randomUUID();
        this.careLogId = careLogId;
        this.milestoneId = milestoneId;
        this.kind = kind;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sortOrder = sortOrder;
    }

    public static Media forCareLog(UUID careLogId, MediaKind kind, String objectKey,
            String contentType, int sortOrder) {
        return new Media(careLogId, null, kind, objectKey, contentType, sortOrder);
    }

    public static Media forMilestone(String milestoneId, MediaKind kind, String objectKey,
            String contentType, int sortOrder) {
        return new Media(null, milestoneId, kind, objectKey, contentType, sortOrder);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCareLogId() {
        return careLogId;
    }

    public String getMilestoneId() {
        return milestoneId;
    }

    public MediaKind getKind() {
        return kind;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
