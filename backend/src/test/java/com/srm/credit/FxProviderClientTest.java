package com.srm.credit;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.credit.exception.FxProviderUnavailableException;
import com.srm.credit.service.FxProviderClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class FxProviderClientTest {
  FxHttpFixture http;
  SimpleMeterRegistry meters;
  FxProviderClient client;

  @BeforeEach
  void start() throws Exception {
    http = new FxHttpFixture();
    meters = new SimpleMeterRegistry();
    var builder = Jackson2ObjectMapperBuilder.json();
    new com.srm.credit.config.ApiConfig().strictApiTypes().customize(builder);
    ObjectMapper mapper = builder.build();
    mapper.enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    client = new FxProviderClient(http.uri(), mapper, Clock.systemUTC(), meters);
  }

  @AfterEach
  void stop() {
    http.close();
    meters.close();
  }

  @Test
  void recoversFrom5xxAndWaits100MillisecondsBeforeSecondAttempt() {
    http.enqueue(503, "unavailable");
    assertThat(client.fetch().rate()).isEqualTo("5.4321");
    assertThat(http.calls).hasValue(2);
    var instants = http.started.toArray(Long[]::new);
    assertThat(Duration.ofNanos(instants[1] - instants[0]))
        .isGreaterThanOrEqualTo(Duration.ofMillis(100));
    assertThat(meters.get("fx.sync.attempts").tag("outcome", "http_5xx").counter().count())
        .isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 404, 429})
  void neverRetries4xx(int status) {
    http.enqueue(status, "rejected");
    assertThatThrownBy(client::fetch).isInstanceOf(FxProviderUnavailableException.class);
    assertThat(http.calls).hasValue(1);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "null",
        "broken-json",
        "{\"rate\":5,\"origin\":\"test\",\"effectiveAt\":\"2026-10-28T14:00:00Z\"}",
        "{\"rate\":\"0\",\"origin\":\"test\",\"effectiveAt\":\"2026-10-28T14:00:00Z\"}",
        "{\"rate\":\"5\",\"origin\":\" \",\"effectiveAt\":\"2026-10-28T14:00:00Z\"}",
        "{\"rate\":\"5\",\"origin\":\"test\",\"effectiveAt\":null}"
      })
  void neverRetriesInvalidPayload(String payload) {
    http.enqueue(200, payload);
    assertThatThrownBy(client::fetch).isInstanceOf(FxProviderUnavailableException.class);
    assertThat(http.calls).hasValue(1);
    assertThat(meters.get("fx.sync.attempts").tag("outcome", "invalid_payload").counter().count())
        .isEqualTo(1);
  }

  @Test
  void persistent5xxStopsAfterTwoAttempts() {
    http.enqueue(500, "failure");
    http.enqueue(503, "failure");
    long start = System.nanoTime();
    assertThatThrownBy(client::fetch).isInstanceOf(FxProviderUnavailableException.class);
    assertThat(http.calls).hasValue(2);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void boundsTwoTimeoutsIncludingStalledBodyToThreeSeconds(boolean body) {
    http.replies.add(new FxHttpFixture.Reply(200, FxHttpFixture.VALID, !body, body));
    http.replies.add(new FxHttpFixture.Reply(200, FxHttpFixture.VALID, !body, body));
    long start = System.nanoTime();
    assertThatThrownBy(client::fetch).isInstanceOf(FxProviderUnavailableException.class);
    assertThat(Duration.ofNanos(System.nanoTime() - start))
        .isBetween(Duration.ofSeconds(2), Duration.ofSeconds(3));
    assertThat(http.calls).hasValue(2);
  }

  @Test
  void retriesConnectionFailureOnlyTwice() {
    http.server.stop(0);
    assertThatThrownBy(client::fetch).isInstanceOf(FxProviderUnavailableException.class);
    assertThat(
            meters.get("fx.sync.attempts").tag("outcome", "connection_failure").counter().count())
        .isEqualTo(2);
  }
}
