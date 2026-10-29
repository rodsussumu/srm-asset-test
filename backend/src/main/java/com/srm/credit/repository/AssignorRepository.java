package com.srm.credit.repository;

import com.srm.credit.model.Assignor;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignorRepository extends JpaRepository<Assignor, UUID> {}
