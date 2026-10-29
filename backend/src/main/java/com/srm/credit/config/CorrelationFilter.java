package com.srm.credit.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(CorrelationFilter.class);
  public static final String HEADER = "X-Correlation-ID";
  public static final String ATTRIBUTE = "correlationId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String supplied = request.getHeader(HEADER);
    String id =
        supplied != null && supplied.matches("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
            ? UUID.fromString(supplied).toString()
            : UUID.randomUUID().toString();
    request.setAttribute(ATTRIBUTE, id);
    response.setHeader(HEADER, id);
    MDC.put(ATTRIBUTE, id);
    boolean completed = false;
    try {
      chain.doFilter(request, response);
      completed = true;
    } finally {
      LOG.atInfo()
          .addKeyValue("event", "http_request")
          .addKeyValue("method", request.getMethod())
          .addKeyValue("status", response.getStatus())
          .addKeyValue("outcome", completed && response.getStatus() < 400 ? "success" : "failure")
          .log("Requisição processada.");
      MDC.remove(ATTRIBUTE);
    }
  }
}
