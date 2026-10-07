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

import java.time.OffsetDateTime;

@Entity
@Table(name = "book_reservations")
public class BookReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reader_id", nullable = false)
    private User reader;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_copy_id")
    private BookCopy bookCopy;

    @Column(nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "reserved_at", nullable = false)
    private OffsetDateTime reservedAt = OffsetDateTime.now();

    @Column(name = "pickup_deadline")
    private OffsetDateTime pickupDeadline;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancelled_by_name", length = 255)
    private String cancelledByName;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    public Long getCancelledBy() { return cancelledBy; }
    public String getCancelledByName() { return cancelledByName; }
    public OffsetDateTime getCancelledAt() { return cancelledAt; }

    public void cancel(User actor, OffsetDateTime time, String reason) {
        this.status = "CANCELLED";
        this.cancelledBy = actor.getId();
        this.cancelledByName = actor.getFullName();
        this.cancelledAt = time;
        this.cancellationReason = reason;
    }

    public void cancelByStaff(User actor, OffsetDateTime time, String reason) {
        cancel(actor, time, reason);
    }

    public BookReservation() {}

    public BookReservation(Book book, User reader, String status) {
        this.book = book;
        this.reader = reader;
        this.status = status;
        this.reservedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Book getBook() {
        return book;
    }

    public void setBook(Book book) {
        this.book = book;
    }

    public User getReader() {
        return reader;
    }

    public void setReader(User reader) {
        this.reader = reader;
    }

    public BookCopy getBookCopy() {
        return bookCopy;
    }

    public void setBookCopy(BookCopy bookCopy) {
        this.bookCopy = bookCopy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getReservedAt() {
        return reservedAt;
    }

    public void setReservedAt(OffsetDateTime reservedAt) {
        this.reservedAt = reservedAt;
    }

    public OffsetDateTime getPickupDeadline() {
        return pickupDeadline;
    }

    public void setPickupDeadline(OffsetDateTime pickupDeadline) {
        this.pickupDeadline = pickupDeadline;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }
}
