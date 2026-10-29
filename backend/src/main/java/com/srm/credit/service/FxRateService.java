package com.srm.credit.service;

import com.srm.credit.dto.CreateFxRateRequest;
import com.srm.credit.exception.FxRateConflictException;
import com.srm.credit.exception.FxRateUnavailableException;
import com.srm.credit.exception.ResourceNotFoundException;
import com.srm.credit.model.FinancialAmounts;
import com.srm.credit.model.FxRate;
import com.srm.credit.repository.FxRateRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FxRateService {
  private final FxRateRepository repository;
  private final Clock clock;

  public FxRateService(FxRateRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional
  public FxRate create(CreateFxRateRequest request) {
    FxRate rate =
        new FxRate(
            FinancialAmounts.parseFxRate(request.rate()),
            request.origin(),
            request.effectiveAt(),
            clock.instant());
    if (insert(rate) == 0) {
      throw new FxRateConflictException();
    }
    return find(rate.getId());
  }

  private int insert(FxRate rate) {
    return repository.insertIfAbsent(
        rate.getId(),
        rate.getRate(),
        rate.getOrigin(),
        rate.getEffectiveAt(),
        rate.getRecordedAt());
  }

  @Transactional
  public SyncResult recordSynchronized(CreateFxRateRequest request) {
    FxRate rate =
        new FxRate(
            FinancialAmounts.parseFxRate(request.rate()),
            request.origin(),
            request.effectiveAt(),
            clock.instant());
    if (insert(rate) == 1) {
      return new SyncResult(find(rate.getId()), true);
    }
    var existing =
        repository
            .findByPairAndEffectiveAt(FxRate.PAIR, rate.getEffectiveAt())
            .orElseThrow(FxRateConflictException::new);
    if (existing.getRate().compareTo(rate.getRate()) != 0) {
      throw new FxRateConflictException();
    }
    return new SyncResult(existing, false);
  }

  public record SyncResult(FxRate rate, boolean created) {}

  public FxRate find(UUID id) {
    return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Cotação"));
  }

  public FxRate current() {
    return selectAt(clock.instant());
  }

  public FxRate selectAt(Instant reference) {
    FxRate rate =
        repository
            .findFirstByPairAndEffectiveAtLessThanEqualAndRecordedAtLessThanEqualOrderByEffectiveAtDesc(
                FxRate.PAIR, reference, reference)
            .orElseThrow(FxRateUnavailableException::new);
    if (Duration.between(rate.getEffectiveAt(), reference).compareTo(Duration.ofHours(24)) > 0) {
      throw new FxRateUnavailableException();
    }
    return rate;
  }

  public Page<FxRate> list(int page, int size) {
    return repository.findAll(
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "effectiveAt", "id")));
  }
}
