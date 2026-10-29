package com.srm.credit.repository;

import com.srm.credit.model.Receivable;
import com.srm.credit.model.ReceivableStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceivableRepository extends JpaRepository<Receivable, UUID> {
  @Override
  @EntityGraph(attributePaths = "assignor")
  Page<Receivable> findAll(Pageable pageable);

  @Override
  @EntityGraph(attributePaths = "assignor")
  Optional<Receivable> findById(UUID id);

  @EntityGraph(attributePaths = "assignor")
  Page<Receivable> findByStatus(ReceivableStatus status, Pageable pageable);
}
