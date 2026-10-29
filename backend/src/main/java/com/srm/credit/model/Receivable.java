package com.srm.credit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "receivables")
public class Receivable {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "assignor_id", nullable = false)
  private Assignor assignor;

  @Column(name = "face_value", nullable = false, precision = 18, scale = 2)
  private BigDecimal faceValue;

  @Column(name = "face_currency", nullable = false, length = 3)
  private String faceCurrency;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ReceivableType type;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_currency", nullable = false, length = 3)
  private PaymentCurrency paymentCurrency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ReceivableStatus status;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Receivable() {}

  public Receivable(
      Assignor assignor,
      BigDecimal faceValue,
      LocalDate dueDate,
      ReceivableType type,
      PaymentCurrency paymentCurrency,
      Instant createdAt) {
    this.assignor = Objects.requireNonNull(assignor);
    this.faceValue = FinancialAmounts.requireFace(faceValue);
    this.faceCurrency = "BRL";
    this.dueDate = Objects.requireNonNull(dueDate);
    this.type = Objects.requireNonNull(type);
    this.paymentCurrency = Objects.requireNonNull(paymentCurrency);
    this.status = ReceivableStatus.AVAILABLE;
    this.createdAt = Objects.requireNonNull(createdAt);
  }

  public UUID getId() {
    return id;
  }

  public Assignor getAssignor() {
    return assignor;
  }

  public BigDecimal getFaceValue() {
    return faceValue;
  }

  public String getFaceCurrency() {
    return faceCurrency;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public ReceivableType getType() {
    return type;
  }

  public PaymentCurrency getPaymentCurrency() {
    return paymentCurrency;
  }

  public ReceivableStatus getStatus() {
    return status;
  }

  public Long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
