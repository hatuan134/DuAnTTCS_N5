package com.duanttcsn5.library.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "book_copies")
public class BookCopy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false, updatable = false)
    private Book book;

    @Column(nullable = false, unique = true, length = 100)
    private String barcode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shelf_id", nullable = false)
    private Shelf shelf;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "received_date")
    private LocalDate receivedDate;

    @Column(name = "cover_price", precision = 12, scale = 2)
    private BigDecimal coverPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "physical_condition", length = 30)
    private PhysicalCondition physicalCondition;

    public Long getId() { return id; }
    public Book getBook() { return book; }
    public String getBarcode() { return barcode; }
    public Shelf getShelf() { return shelf; }
    public String getStatus() { return status; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public BigDecimal getCoverPrice() { return coverPrice; }
    public PhysicalCondition getPhysicalCondition() { return physicalCondition; }
}
