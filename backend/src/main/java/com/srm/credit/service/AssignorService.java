package com.srm.credit.service;

import com.srm.credit.exception.ResourceNotFoundException;
import com.srm.credit.model.Assignor;
import com.srm.credit.repository.AssignorRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AssignorService {
  private final AssignorRepository repository;
  private final Clock clock;

  public AssignorService(AssignorRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional
  public Assignor create(String name) {
    return repository.saveAndFlush(new Assignor(name, clock.instant()));
  }

  public Assignor find(UUID id) {
    return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Cedente"));
  }

  public Page<Assignor> list(int page, int size) {
    return repository.findAll(
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
  }
}
