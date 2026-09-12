package com.bb.progress.growth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "growth_record")
public class GrowthRecord {

    @Id
    private UUID id;

    @Column(name = "measured_on", nullable = false, unique = true)
    private LocalDate measuredOn;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "height_cm")
    private BigDecimal heightCm;

    @Column(name = "head_circumference_cm")
    private BigDecimal headCircumferenceCm;

    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GrowthRecord() {
    }

    public GrowthRecord(LocalDate measuredOn, BigDecimal weightKg, BigDecimal heightCm,
            BigDecimal headCircumferenceCm, String note) {
        this.id = UUID.randomUUID();
        this.measuredOn = measuredOn;
        this.weightKg = weightKg;
        this.heightCm = heightCm;
        this.headCircumferenceCm = headCircumferenceCm;
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

    public LocalDate getMeasuredOn() {
        return measuredOn;
    }

    public void setMeasuredOn(LocalDate measuredOn) {
        this.measuredOn = measuredOn;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public BigDecimal getHeadCircumferenceCm() {
        return headCircumferenceCm;
    }

    public void setHeadCircumferenceCm(BigDecimal headCircumferenceCm) {
        this.headCircumferenceCm = headCircumferenceCm;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
