package io.github.aniketdeshkar.mcpgateway;

import java.util.Map;
import java.util.Objects;

public record ToolDescriptor(
    String serverId, String name, String description, Map<String, Object> inputSchema) {
  public ToolDescriptor {
    serverId = requireText(serverId, "serverId");
    name = requireText(name, "name");
    description = Objects.requireNonNullElse(description, "");
    inputSchema = Map.copyOf(Objects.requireNonNull(inputSchema, "inputSchema"));
  }

  public String qualifiedName() {
    return serverId + "." + name;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}
