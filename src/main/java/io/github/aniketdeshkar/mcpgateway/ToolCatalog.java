package io.github.aniketdeshkar.mcpgateway;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ToolCatalog {
  private final ServerRegistry registry;
  private final ToolPolicy policy;
  private final ConcurrentMap<String, ToolDescriptor> tools = new ConcurrentHashMap<>();

  public ToolCatalog(ServerRegistry registry, ToolPolicy policy) {
    this.registry = registry;
    this.policy = policy;
  }

  public List<ToolDescriptor> refresh() {
    Map<String, ToolDescriptor> discovered = new ConcurrentHashMap<>();
    for (McpServer server : registry.snapshot()) {
      for (ToolDescriptor tool : server.discoverTools()) {
        if (!server.id().equals(tool.serverId())) {
          throw new IllegalStateException("tool server id does not match registry id");
        }
        if (policy.permits(tool.qualifiedName())
            && discovered.putIfAbsent(tool.qualifiedName(), tool) != null) {
          throw new IllegalStateException("duplicate qualified tool: " + tool.qualifiedName());
        }
      }
    }
    tools.clear();
    tools.putAll(discovered);
    return list();
  }

  public List<ToolDescriptor> list() {
    return tools.values().stream()
        .sorted((left, right) -> left.qualifiedName().compareTo(right.qualifiedName()))
        .toList();
  }

  public Optional<ToolDescriptor> find(String qualifiedName) {
    return Optional.ofNullable(tools.get(qualifiedName));
  }
}
