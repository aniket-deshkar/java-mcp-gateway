package io.github.aniketdeshkar.mcpgateway;

@FunctionalInterface
public interface AuditSink {
  void record(AuditEvent event);

  static AuditSink noOp() {
    return event -> {};
  }
}
