package io.github.aniketdeshkar.mcpgateway;

@FunctionalInterface
public interface GatewayTelemetry {
  Scope start(ToolCall call, ToolDescriptor tool);

  static GatewayTelemetry noOp() {
    return (call, tool) -> (outcome, error) -> {};
  }

  @FunctionalInterface
  interface Scope {
    void close(AuditEvent.Outcome outcome, Throwable error);
  }
}
