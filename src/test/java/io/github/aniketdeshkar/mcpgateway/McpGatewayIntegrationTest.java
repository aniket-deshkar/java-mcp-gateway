package io.github.aniketdeshkar.mcpgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class McpGatewayIntegrationTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void discoversAndRoutesAcrossTwoNamespacedServers() {
    Fixture fixture = fixture(Set.of("math.add", "text.upper"));

    assertEquals(
        List.of("math.add", "text.upper"),
        fixture.catalog().refresh().stream().map(ToolDescriptor::qualifiedName).toList());
    assertEquals(
        5,
        fixture
            .gateway()
            .invoke(new ToolCall("r-1", "alice", "math.add", Map.of("left", 2, "right", 3)))
            .content()
            .get("value"));
    assertEquals(
        "HELLO",
        fixture
            .gateway()
            .invoke(new ToolCall("r-2", "alice", "text.upper", Map.of("value", "hello")))
            .content()
            .get("value"));
  }

  @Test
  void prohibitedToolIsNotCatalogedOrForwarded() {
    AtomicInteger invocations = new AtomicInteger();
    ServerRegistry registry = new ServerRegistry();
    registry.register(
        server(
            "admin",
            "delete",
            arguments -> {
              invocations.incrementAndGet();
              return ToolResult.success(Map.of());
            }));
    ToolPolicy policy = new ToolPolicy(Set.of(), Set.of("admin.delete"));
    ToolCatalog catalog = new ToolCatalog(registry, policy);
    catalog.refresh();
    McpGateway gateway =
        gateway(
            registry,
            catalog,
            policy,
            AuthorizationHook.allowAll(),
            Quota.unlimited(),
            new ArrayList<>());

    GatewayException error =
        assertThrows(GatewayException.class, () -> gateway.invoke(call("admin.delete")));

    assertEquals(GatewayException.Code.UNKNOWN_TOOL, error.code());
    assertEquals(0, invocations.get());
  }

  @Test
  void authorizationRunsBeforeForwarding() {
    AtomicInteger invocations = new AtomicInteger();
    ServerRegistry registry = new ServerRegistry();
    registry.register(
        server(
            "math",
            "add",
            arguments -> {
              invocations.incrementAndGet();
              return ToolResult.success(Map.of());
            }));
    ToolPolicy policy = new ToolPolicy(Set.of("math.add"), Set.of());
    ToolCatalog catalog = new ToolCatalog(registry, policy);
    catalog.refresh();
    McpGateway gateway =
        gateway(
            registry,
            catalog,
            policy,
            (principal, tool) -> false,
            Quota.unlimited(),
            new ArrayList<>());

    GatewayException error =
        assertThrows(GatewayException.class, () -> gateway.invoke(call("math.add")));

    assertEquals(GatewayException.Code.FORBIDDEN, error.code());
    assertEquals(0, invocations.get());
  }

  @Test
  void appliesQuotaPerServerAndAuditsRejection() {
    Fixture fixture = fixture(Set.of("math.add"));
    List<AuditEvent> events = new ArrayList<>();
    McpGateway gateway =
        gateway(
            fixture.registry(),
            fixture.catalog(),
            fixture.policy(),
            AuthorizationHook.allowAll(),
            new FixedWindowQuota(1, Duration.ofMinutes(1), CLOCK),
            events);

    gateway.invoke(call("math.add"));
    GatewayException error =
        assertThrows(GatewayException.class, () -> gateway.invoke(call("math.add")));

    assertEquals(GatewayException.Code.QUOTA_EXCEEDED, error.code());
    assertEquals(
        List.of(AuditEvent.Outcome.ALLOWED, AuditEvent.Outcome.REJECTED),
        events.stream().map(AuditEvent::outcome).toList());
  }

  @Test
  void rejectsDownServer() {
    ServerRegistry registry = new ServerRegistry();
    registry.register(
        new LocalMcpServer(
            "down",
            Map.of("read", tool(arguments -> ToolResult.success(Map.of()))),
            () -> ServerHealth.DOWN));
    ToolPolicy policy = new ToolPolicy(Set.of("down.read"), Set.of());
    ToolCatalog catalog = new ToolCatalog(registry, policy);
    catalog.refresh();

    GatewayException error =
        assertThrows(
            GatewayException.class,
            () ->
                gateway(
                        registry,
                        catalog,
                        policy,
                        AuthorizationHook.allowAll(),
                        Quota.unlimited(),
                        new ArrayList<>())
                    .invoke(call("down.read")));

    assertEquals(GatewayException.Code.SERVER_UNAVAILABLE, error.code());
  }

  @Test
  void recordsFailedUpstreamWithoutLeakingCauseMessage() {
    ServerRegistry registry = new ServerRegistry();
    registry.register(
        server(
            "vault",
            "read",
            arguments -> {
              throw new IllegalStateException("secret detail");
            }));
    ToolPolicy policy = new ToolPolicy(Set.of("vault.read"), Set.of());
    ToolCatalog catalog = new ToolCatalog(registry, policy);
    catalog.refresh();
    List<AuditEvent> events = new ArrayList<>();

    GatewayException error =
        assertThrows(
            GatewayException.class,
            () ->
                gateway(
                        registry,
                        catalog,
                        policy,
                        AuthorizationHook.allowAll(),
                        Quota.unlimited(),
                        events)
                    .invoke(call("vault.read")));

    assertEquals("upstream tool call failed", error.getMessage());
    assertFalse(error.getMessage().contains("secret"));
    assertEquals(AuditEvent.Outcome.FAILED, events.getFirst().outcome());
  }

  @Test
  void emitsTelemetryForAllowedAndFailedCalls() {
    Fixture fixture = fixture(Set.of("math.add"));
    List<AuditEvent.Outcome> outcomes = new ArrayList<>();
    GatewayTelemetry telemetry = (call, tool) -> (outcome, error) -> outcomes.add(outcome);
    McpGateway gateway =
        new McpGateway(
            fixture.registry(),
            fixture.catalog(),
            fixture.policy(),
            AuthorizationHook.allowAll(),
            Quota.unlimited(),
            AuditSink.noOp(),
            telemetry,
            CLOCK);

    gateway.invoke(call("math.add"));

    assertEquals(List.of(AuditEvent.Outcome.ALLOWED), outcomes);
  }

  @Test
  void apiKeyAuthenticatorUsesConfiguredPrincipal() {
    ApiKeyAuthenticator authenticator = new ApiKeyAuthenticator(Map.of("service-a", "correct-key"));

    assertEquals("service-a", authenticator.authenticate("correct-key").orElseThrow());
    assertTrue(authenticator.authenticate("wrong-key").isEmpty());
    assertTrue(authenticator.authenticate(null).isEmpty());
  }

  private static Fixture fixture(Set<String> allowed) {
    ServerRegistry registry = new ServerRegistry();
    registry.register(
        server(
            "math",
            "add",
            arguments ->
                ToolResult.success(
                    Map.of(
                        "value",
                        ((Integer) arguments.get("left")) + ((Integer) arguments.get("right"))))));
    registry.register(
        server(
            "text",
            "upper",
            arguments ->
                ToolResult.success(
                    Map.of("value", arguments.get("value").toString().toUpperCase()))));
    ToolPolicy policy = new ToolPolicy(allowed, Set.of());
    ToolCatalog catalog = new ToolCatalog(registry, policy);
    catalog.refresh();
    return new Fixture(
        registry,
        catalog,
        policy,
        gateway(
            registry,
            catalog,
            policy,
            AuthorizationHook.allowAll(),
            Quota.unlimited(),
            new ArrayList<>()));
  }

  private static McpGateway gateway(
      ServerRegistry registry,
      ToolCatalog catalog,
      ToolPolicy policy,
      AuthorizationHook authorization,
      Quota quota,
      List<AuditEvent> events) {
    return new McpGateway(
        registry,
        catalog,
        policy,
        authorization,
        quota,
        events::add,
        GatewayTelemetry.noOp(),
        CLOCK);
  }

  private static LocalMcpServer server(
      String id,
      String name,
      java.util.function.Function<Map<String, Object>, ToolResult> handler) {
    return new LocalMcpServer(id, Map.of(name, tool(handler)), () -> ServerHealth.UP);
  }

  private static LocalMcpServer.Tool tool(
      java.util.function.Function<Map<String, Object>, ToolResult> handler) {
    return new LocalMcpServer.Tool("test tool", Map.of("type", "object"), handler);
  }

  private static ToolCall call(String tool) {
    return new ToolCall("request-1", "alice", tool, Map.of("left", 1, "right", 2));
  }

  private record Fixture(
      ServerRegistry registry, ToolCatalog catalog, ToolPolicy policy, McpGateway gateway) {}
}
