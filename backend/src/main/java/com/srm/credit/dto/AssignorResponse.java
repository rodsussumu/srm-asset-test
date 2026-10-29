package com.srm.credit.dto;

import com.srm.credit.model.Assignor;
import java.time.Instant;
import java.util.UUID;

public record AssignorResponse(UUID id, String name, Instant createdAt) {
  public static AssignorResponse from(Assignor assignor) {
    return new AssignorResponse(assignor.getId(), assignor.getName(), assignor.getCreatedAt());
  }
}
