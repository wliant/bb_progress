package com.bb.progress.headshape;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A day's head shape record. The photos hang off it as {@code media} rows. */
@Entity
@Table(name = "head_shape_entry")
public class HeadShapeEntry {

    @Id
    private UUID id;

    /** The SGT calendar day, unique: the day is the unit of comparison. */
    @Column(name = "recorded_on", nullable = false)
    private LocalDate recordedOn;

    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HeadShapeEntry() {
    }

    public HeadShapeEntry(LocalDate recordedOn, String note) {
        this.id = UUID.randomUUID();
        this.recordedOn = recordedOn;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getRecordedOn() {
        return recordedOn;
    }

    public void setRecordedOn(LocalDate recordedOn) {
        this.recordedOn = recordedOn;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
