package io.github.aniketdeshkar.mcpgateway;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ServerRegistry {
  private final ConcurrentMap<String, McpServer> servers = new ConcurrentHashMap<>();

  public void register(McpServer server) {
    Objects.requireNonNull(server, "server");
    if (server.id().isBlank()) {
      throw new IllegalArgumentException("server id must not be blank");
    }
    if (servers.putIfAbsent(server.id(), server) != null) {
      throw new IllegalArgumentException("server already registered: " + server.id());
    }
  }

  public Optional<McpServer> find(String id) {
    return Optional.ofNullable(servers.get(id));
  }

  public List<McpServer> snapshot() {
    return servers.values().stream()
        .sorted((left, right) -> left.id().compareTo(right.id()))
        .toList();
  }
}
