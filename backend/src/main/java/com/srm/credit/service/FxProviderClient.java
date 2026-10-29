package com.srm.credit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.credit.dto.CreateFxRateRequest;
import com.srm.credit.exception.FxProviderUnavailableException;
import com.srm.credit.exception.InvalidInputException;
import com.srm.credit.model.FinancialAmounts;
import com.srm.credit.model.FxRate;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FxProviderClient {
  private static final Logger LOG = LoggerFactory.getLogger(FxProviderClient.class);
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
  private final URI uri;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final MeterRegistry meters;

  public FxProviderClient(
      @Value("${fx.provider.url}") URI uri,
      ObjectMapper mapper,
      Clock clock,
      MeterRegistry meters) {
    this.uri = uri;
    this.mapper = mapper;
    this.clock = clock;
    this.meters = meters;
  }

  public CreateFxRateRequest fetch() {
    for (int attempt = 1; attempt <= 2; attempt++) {
      var pending =
          client.sendAsync(
              HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(1)).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      try {
        // Bounds the entire response, including a stalled body after successful headers.
        var response = pending.get(1, TimeUnit.SECONDS);
        int status = response.statusCode();
        if (status >= 500 && status <= 599) {
          record(attempt, "http_5xx");
        } else if (status != 200) {
          record(attempt, "http_rejected");
          throw new FxProviderUnavailableException();
        } else {
          CreateFxRateRequest quote;
          try {
            quote = mapper.readValue(response.body(), CreateFxRateRequest.class);
            if (quote == null) {
              throw new InvalidInputException("body", "Cotação obrigatória.");
            }
            new FxRate(
                FinancialAmounts.parseFxRate(quote.rate()),
                quote.origin(),
                quote.effectiveAt(),
                clock.instant());
          } catch (JsonProcessingException | InvalidInputException invalid) {
            record(attempt, "invalid_payload");
            throw new FxProviderUnavailableException();
          }
          record(attempt, "success");
          return quote;
        }
      } catch (TimeoutException timeout) {
        pending.cancel(true);
        record(attempt, "timeout");
      } catch (ExecutionException failure) {
        record(
            attempt,
            failure.getCause() instanceof java.net.http.HttpTimeoutException
                ? "timeout"
                : "connection_failure");
      } catch (InterruptedException interrupted) {
        pending.cancel(true);
        Thread.currentThread().interrupt();
        record(attempt, "interrupted");
        throw new FxProviderUnavailableException();
      }
      if (attempt == 1) {
        try {
          TimeUnit.MILLISECONDS.sleep(100);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new FxProviderUnavailableException();
        }
      }
    }
    throw new FxProviderUnavailableException();
  }

  private void record(int attempt, String outcome) {
    meters.counter("fx.sync.attempts", "outcome", outcome).increment();
    LOG.info("event=fx_sync_attempt attempt={} outcome={}", attempt, outcome);
  }
}
