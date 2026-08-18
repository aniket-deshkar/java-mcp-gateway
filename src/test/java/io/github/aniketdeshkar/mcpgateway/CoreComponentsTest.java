package io.github.aniketdeshkar.mcpgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CoreComponentsTest {
  @Test
  void rejectsDuplicateServerIds() {
    ServerRegistry registry = new ServerRegistry();
    registry.register(server("one", "read"));

    assertThrows(IllegalArgumentException.class, () -> registry.register(server("one", "write")));
  }

  @Test
  void denyRuleOverridesAllowRule() {
    ToolPolicy policy = new ToolPolicy(Set.of("one.read"), Set.of("one.read"));

    assertTrue(!policy.permits("one.read"));
  }

  @Test
  void catalogRejectsMismatchedServerIdentity() {
    ServerRegistry registry = new ServerRegistry();
    registry.register(
        new McpServer() {
          public String id() {
            return "registered";
          }

          public java.util.List<ToolDescriptor> discoverTools() {
            return java.util.List.of(new ToolDescriptor("other", "read", "", Map.of()));
          }

          public ToolResult invoke(String name, Map<String, Object> arguments) {
            return ToolResult.success(Map.of());
          }

          public ServerHealth health() {
            return ServerHealth.UP;
          }
        });
    ToolCatalog catalog = new ToolCatalog(registry, new ToolPolicy(Set.of("other.read"), Set.of()));

    assertThrows(IllegalStateException.class, catalog::refresh);
  }

  @Test
  void fixedWindowQuotaRejectsInvalidConfiguration() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new FixedWindowQuota(0, Duration.ofSeconds(1), Clock.systemUTC()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new FixedWindowQuota(1, Duration.ZERO, Clock.systemUTC()));
  }

  @Test
  void fixedWindowQuotaResetsOnBoundary() {
    MutableClock clock = new MutableClock(Instant.EPOCH);
    FixedWindowQuota quota = new FixedWindowQuota(1, Duration.ofSeconds(10), clock);

    assertTrue(quota.tryAcquire("one"));
    assertTrue(!quota.tryAcquire("one"));
    clock.instant = Instant.EPOCH.plusSeconds(10);
    assertTrue(quota.tryAcquire("one"));
  }

  @Test
  void descriptorsAndCallsValidateRequiredFields() {
    assertThrows(
        IllegalArgumentException.class, () -> new ToolDescriptor("", "read", "", Map.of()));
    assertThrows(
        IllegalArgumentException.class, () -> new ToolCall("id", "", "one.read", Map.of()));
    assertEquals("one.read", new ToolDescriptor("one", "read", "", Map.of()).qualifiedName());
  }

  private static LocalMcpServer server(String id, String tool) {
    return new LocalMcpServer(
        id,
        Map.of(
            tool, new LocalMcpServer.Tool("", Map.of(), arguments -> ToolResult.success(Map.of()))),
        () -> ServerHealth.UP);
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
