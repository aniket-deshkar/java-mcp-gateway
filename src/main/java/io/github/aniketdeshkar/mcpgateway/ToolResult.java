package io.github.aniketdeshkar.mcpgateway;

import java.util.Map;

public record ToolResult(Map<String, Object> content, boolean error) {
  public ToolResult {
    content = Map.copyOf(content);
  }

  public static ToolResult success(Map<String, Object> content) {
    return new ToolResult(content, false);
  }
}
