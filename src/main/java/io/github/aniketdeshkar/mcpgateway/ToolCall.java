package io.github.aniketdeshkar.mcpgateway;

import java.util.Map;
import java.util.Objects;

public record ToolCall(
    String requestId, String principal, String qualifiedTool, Map<String, Object> arguments) {
  public ToolCall {
    requestId = requireText(requestId, "requestId");
    principal = requireText(principal, "principal");
    qualifiedTool = requireText(qualifiedTool, "qualifiedTool");
    arguments = Map.copyOf(Objects.requireNonNull(arguments, "arguments"));
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}
