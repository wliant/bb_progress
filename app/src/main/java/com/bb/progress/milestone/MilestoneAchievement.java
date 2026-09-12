package com.bb.progress.milestone;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "milestone_achievement")
public class MilestoneAchievement {

    @Id
    private UUID id;

    @Column(name = "milestone_id", nullable = false, unique = true)
    private String milestoneId;

    @Column(name = "achieved_on", nullable = false)
    private LocalDate achievedOn;

    @Column(name = "photo_path")
    private String photoPath;

    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MilestoneAchievement() {
    }

    public MilestoneAchievement(String milestoneId, LocalDate achievedOn, String note) {
        this.id = UUID.randomUUID();
        this.milestoneId = milestoneId;
        this.achievedOn = achievedOn;
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

    public String getMilestoneId() {
        return milestoneId;
    }

    public LocalDate getAchievedOn() {
        return achievedOn;
    }

    public void setAchievedOn(LocalDate achievedOn) {
        this.achievedOn = achievedOn;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
