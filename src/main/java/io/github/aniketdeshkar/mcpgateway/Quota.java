package io.github.aniketdeshkar.mcpgateway;

@FunctionalInterface
public interface Quota {
  boolean tryAcquire(String serverId);

  static Quota unlimited() {
    return serverId -> true;
  }
}
