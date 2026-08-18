package io.github.aniketdeshkar.mcpgateway;

@FunctionalInterface
public interface AuthorizationHook {
  boolean permits(String principal, ToolDescriptor tool);

  static AuthorizationHook allowAll() {
    return (principal, tool) -> true;
  }
}
