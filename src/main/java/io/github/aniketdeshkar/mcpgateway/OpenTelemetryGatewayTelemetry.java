package io.github.aniketdeshkar.mcpgateway;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

public final class OpenTelemetryGatewayTelemetry implements GatewayTelemetry {
  private static final AttributeKey<String> TOOL_NAME = AttributeKey.stringKey("mcp.tool.name");
  private static final AttributeKey<String> SERVER_ID = AttributeKey.stringKey("mcp.server.id");
  private final Tracer tracer;

  public OpenTelemetryGatewayTelemetry(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public Scope start(ToolCall call, ToolDescriptor tool) {
    Span span = tracer.spanBuilder("mcp.tool.call").startSpan();
    span.setAttribute(TOOL_NAME, tool.qualifiedName());
    span.setAttribute(SERVER_ID, tool.serverId());
    return (outcome, error) -> {
      if (error != null) {
        span.recordException(error);
        span.setStatus(StatusCode.ERROR);
      }
      span.end();
    };
  }
}
