package io.github.aniketdeshkar.mcpgateway;

import java.util.Set;

public final class ToolPolicy {
  private final Set<String> allowed;
  private final Set<String> denied;

  public ToolPolicy(Set<String> allowed, Set<String> denied) {
    this.allowed = Set.copyOf(allowed);
    this.denied = Set.copyOf(denied);
  }

  public boolean permits(String qualifiedName) {
    return allowed.contains(qualifiedName) && !denied.contains(qualifiedName);
  }
}
