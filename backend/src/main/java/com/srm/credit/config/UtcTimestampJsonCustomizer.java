package com.srm.credit.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.time.OffsetDateTime;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/** Normalize timestamps without changing the JVM timezone or the financial business clock. */
public class UtcTimestampJsonCustomizer
    implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {
  @Override
  public void customize(JsonWriter.Members<ILoggingEvent> members) {
    members.applyingValueProcessor(
        JsonWriter.ValueProcessor.of(
                String.class, value -> OffsetDateTime.parse(value).toInstant().toString())
            .whenHasUnescapedPath("@timestamp"));
  }
}
