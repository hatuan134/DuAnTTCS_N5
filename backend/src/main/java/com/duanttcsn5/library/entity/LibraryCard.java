package com.duanttcsn5.library.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "library_cards")
public class LibraryCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_number", nullable = false, unique = true, length = 100, updatable = false)
    private String cardNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_type_id", nullable = false)
    private CardType cardType;

    @Column(name = "issued_at", nullable = false)
    private LocalDate issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDate expiresAt;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "manual_lock_reason", length = 1000)
    private String manualLockReason;
    @Column(name = "manual_lock_started_at")
    private OffsetDateTime manualLockStartedAt;
    @Column(name = "manual_lock_ends_at")
    private OffsetDateTime manualLockEndsAt;
    @Column(name = "manual_lock_actor_id")
    private Long manualLockActorId;

    @Column(name = "manual_lock_released_at")
    private OffsetDateTime manualLockReleasedAt;

    public OffsetDateTime getManualLockReleasedAt() { return manualLockReleasedAt; }
    public void releaseManualLock(OffsetDateTime releasedAt) { manualLockReleasedAt = releasedAt; }

    public String getManualLockReason() { return manualLockReason; }
    public OffsetDateTime getManualLockStartedAt() { return manualLockStartedAt; }
    public OffsetDateTime getManualLockEndsAt() { return manualLockEndsAt; }
    public Long getManualLockActorId() { return manualLockActorId; }

    public void applyManualLock(String reason, OffsetDateTime startedAt,
                                OffsetDateTime endsAt, Long actorId) {
        manualLockReason = reason;
        manualLockStartedAt = startedAt;
        manualLockEndsAt = endsAt;
        manualLockActorId = actorId;
        manualLockReleasedAt = null;
    }

    public boolean hasActiveManualLock(OffsetDateTime now) {
        return manualLockReleasedAt == null && manualLockStartedAt != null && !manualLockStartedAt.isAfter(now)
                && (manualLockEndsAt == null || manualLockEndsAt.isAfter(now));
    }

    public String manualLockMessage(OffsetDateTime now) {
        String duration = manualLockEndsAt == null ? "Khóa đến khi mở thủ công."
                : "Khóa đến " + manualLockEndsAt.atZoneSameInstant(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + " (giờ Việt Nam).";
        return "Thẻ thư viện đang bị khóa. Lý do: " + manualLockReason + ". " + duration
                + " Không thể mượn mới, đặt giữ hoặc gia hạn.";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public CardType getCardType() { return cardType; }
    public void setCardType(CardType cardType) { this.cardType = cardType; }
    public LocalDate getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDate issuedAt) { this.issuedAt = issuedAt; }
    public LocalDate getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDate expiresAt) { this.expiresAt = expiresAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
