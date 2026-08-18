package io.github.aniketdeshkar.mcpgateway;

import java.time.Clock;
import java.util.Objects;

public final class McpGateway {
  private final ServerRegistry registry;
  private final ToolCatalog catalog;
  private final ToolPolicy policy;
  private final AuthorizationHook authorization;
  private final Quota quota;
  private final AuditSink audit;
  private final GatewayTelemetry telemetry;
  private final Clock clock;

  public McpGateway(
      ServerRegistry registry,
      ToolCatalog catalog,
      ToolPolicy policy,
      AuthorizationHook authorization,
      Quota quota,
      AuditSink audit,
      GatewayTelemetry telemetry,
      Clock clock) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.catalog = Objects.requireNonNull(catalog, "catalog");
    this.policy = Objects.requireNonNull(policy, "policy");
    this.authorization = Objects.requireNonNull(authorization, "authorization");
    this.quota = Objects.requireNonNull(quota, "quota");
    this.audit = Objects.requireNonNull(audit, "audit");
    this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public ToolResult invoke(ToolCall call) {
    ToolDescriptor tool =
        catalog
            .find(call.qualifiedTool())
            .orElseThrow(
                () -> reject(call, GatewayException.Code.UNKNOWN_TOOL, "tool is not exposed"));
    if (!policy.permits(tool.qualifiedName()) || !authorization.permits(call.principal(), tool)) {
      throw reject(call, GatewayException.Code.FORBIDDEN, "tool call is not authorized");
    }
    if (!quota.tryAcquire(tool.serverId())) {
      throw reject(call, GatewayException.Code.QUOTA_EXCEEDED, "server quota exceeded");
    }
    McpServer server =
        registry
            .find(tool.serverId())
            .orElseThrow(
                () ->
                    reject(
                        call,
                        GatewayException.Code.SERVER_UNAVAILABLE,
                        "server is not registered"));
    if (server.health() == ServerHealth.DOWN) {
      throw reject(call, GatewayException.Code.SERVER_UNAVAILABLE, "server is down");
    }

    GatewayTelemetry.Scope span = telemetry.start(call, tool);
    try {
      ToolResult result = server.invoke(tool.name(), call.arguments());
      audit(call, AuditEvent.Outcome.ALLOWED);
      span.close(AuditEvent.Outcome.ALLOWED, null);
      return result;
    } catch (RuntimeException error) {
      audit(call, AuditEvent.Outcome.FAILED);
      span.close(AuditEvent.Outcome.FAILED, error);
      throw new GatewayException(
          GatewayException.Code.UPSTREAM_FAILURE, "upstream tool call failed");
    }
  }

  private GatewayException reject(ToolCall call, GatewayException.Code code, String message) {
    audit(call, AuditEvent.Outcome.REJECTED);
    return new GatewayException(code, message);
  }

  private void audit(ToolCall call, AuditEvent.Outcome outcome) {
    audit.record(
        new AuditEvent(
            clock.instant(), call.requestId(), call.principal(), call.qualifiedTool(), outcome));
  }
}
