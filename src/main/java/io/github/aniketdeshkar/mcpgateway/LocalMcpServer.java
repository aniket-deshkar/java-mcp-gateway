package io.github.aniketdeshkar.mcpgateway;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** A transport-free local MCP server useful for development, tests, and embedded tools. */
public final class LocalMcpServer implements McpServer {
  private final String id;
  private final Map<String, Tool> tools;
  private final Supplier<ServerHealth> health;

  public LocalMcpServer(String id, Map<String, Tool> tools, Supplier<ServerHealth> health) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
    this.id = id;
    this.tools = Map.copyOf(tools);
    this.health = Objects.requireNonNull(health, "health");
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public List<ToolDescriptor> discoverTools() {
    return tools.entrySet().stream()
        .map(
            entry ->
                new ToolDescriptor(
                    id,
                    entry.getKey(),
                    entry.getValue().description(),
                    entry.getValue().inputSchema()))
        .toList();
  }

  @Override
  public ToolResult invoke(String toolName, Map<String, Object> arguments) {
    Tool tool = tools.get(toolName);
    if (tool == null) {
      throw new IllegalArgumentException("unknown tool: " + toolName);
    }
    return tool.handler().apply(arguments);
  }

  @Override
  public ServerHealth health() {
    return health.get();
  }

  public record Tool(
      String description,
      Map<String, Object> inputSchema,
      Function<Map<String, Object>, ToolResult> handler) {
    public Tool {
      description = Objects.requireNonNullElse(description, "");
      inputSchema = Map.copyOf(inputSchema);
      handler = Objects.requireNonNull(handler, "handler");
    }
  }
}
