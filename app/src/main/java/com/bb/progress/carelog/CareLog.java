package com.bb.progress.carelog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "care_log")
public class CareLog {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CareType type;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    private String note;

    // Feeding detail. Both quantities are independently optional: a bottle records an
    // amount, a breastfeed may record minutes, an amount, or both. See spec 08.
    @Enumerated(EnumType.STRING)
    @Column(name = "feed_method")
    private FeedMethod feedMethod;

    @Column(name = "amount_ml")
    private Integer amountMl;

    @Column(name = "duration_min")
    private Integer durationMin;

    // Sleep window. A null end means the nap is still in progress; the duration is always
    // derived from the pair so an edit cannot leave a stored duration disagreeing with it.
    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition")
    private DiaperCondition condition;

    /** Whether the nappy was wet. Independent of {@link #condition}: a change can be
     *  wet and clean, or dirty and dry. Null on entries recorded before V12. */
    @Column(name = "wet")
    private Boolean wet;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CareLog() {
    }

    public CareLog(CareType type, Instant loggedAt, String note) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.loggedAt = loggedAt;
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

    public CareType getType() {
        return type;
    }

    public Instant getLoggedAt() {
        return loggedAt;
    }

    public void setLoggedAt(Instant loggedAt) {
        this.loggedAt = loggedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public FeedMethod getFeedMethod() {
        return feedMethod;
    }

    public void setFeedMethod(FeedMethod feedMethod) {
        this.feedMethod = feedMethod;
    }

    public Integer getAmountMl() {
        return amountMl;
    }

    public void setAmountMl(Integer amountMl) {
        this.amountMl = amountMl;
    }

    public Integer getDurationMin() {
        return durationMin;
    }

    public void setDurationMin(Integer durationMin) {
        this.durationMin = durationMin;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public DiaperCondition getCondition() {
        return condition;
    }

    public void setCondition(DiaperCondition condition) {
        this.condition = condition;
    }

    public Boolean getWet() {
        return wet;
    }

    public void setWet(Boolean wet) {
        this.wet = wet;
    }

    /** Minutes asleep, or null while the nap is still open. Derived, never stored. */
    public Long getSleepMinutes() {
        if (startedAt == null || endedAt == null) {
            return null;
        }
        return Duration.between(startedAt, endedAt).toMinutes();
    }

}
