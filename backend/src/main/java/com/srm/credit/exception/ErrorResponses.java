package com.srm.credit.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.credit.config.CorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public class ErrorResponses {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ErrorResponses.class);
  private final ObjectMapper mapper;

  public ErrorResponses(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public ProblemDetail problem(
      HttpStatus status, String code, String detail, HttpServletRequest request) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", code);
    Object id = request.getAttribute(CorrelationFilter.ATTRIBUTE);
    problem.setProperty("correlationId", id == null ? UUID.randomUUID().toString() : id.toString());
    LOG.atLevel(
            status.is5xxServerError() ? org.slf4j.event.Level.ERROR : org.slf4j.event.Level.WARN)
        .addKeyValue(
            "actorId",
            request.getUserPrincipal() == null ? null : request.getUserPrincipal().getName())
        .addKeyValue("receivableId", request.getAttribute("receivableId"))
        .addKeyValue("event", "request_failed")
        .addKeyValue("code", code)
        .addKeyValue("status", status.value())
        .addKeyValue("outcome", "failure")
        .log("Requisição recusada.");
    return problem;
  }

  public void write(
      HttpStatus status,
      String code,
      String detail,
      HttpServletRequest request,
      HttpServletResponse response)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    mapper.writeValue(response.getOutputStream(), problem(status, code, detail, request));
  }
}
