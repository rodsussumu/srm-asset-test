package com.srm.credit.model;

import com.srm.credit.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "assignors")
public class Assignor {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Assignor() {}

  public Assignor(String name, Instant createdAt) {
    if (name == null || name.isBlank() || name.strip().length() > 200) {
      throw new InvalidInputException("name", "Nome deve conter entre 1 e 200 caracteres.");
    }
    this.name = name.strip();
    this.createdAt = Objects.requireNonNull(createdAt);
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
