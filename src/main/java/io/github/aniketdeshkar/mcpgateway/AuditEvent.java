package io.github.aniketdeshkar.mcpgateway;

import java.time.Instant;

public record AuditEvent(
    Instant occurredAt, String requestId, String principal, String tool, Outcome outcome) {
  public enum Outcome {
    ALLOWED,
    REJECTED,
    FAILED
  }
}
