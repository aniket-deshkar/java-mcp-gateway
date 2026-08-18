package io.github.aniketdeshkar.mcpgateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

public final class ApiKeyAuthenticator {
  private final Map<String, String> keysByPrincipal;

  public ApiKeyAuthenticator(Map<String, String> keysByPrincipal) {
    this.keysByPrincipal = Map.copyOf(keysByPrincipal);
  }

  public Optional<String> authenticate(String candidate) {
    if (candidate == null) {
      return Optional.empty();
    }
    byte[] actual = candidate.getBytes(StandardCharsets.UTF_8);
    return keysByPrincipal.entrySet().stream()
        .filter(
            entry ->
                MessageDigest.isEqual(entry.getValue().getBytes(StandardCharsets.UTF_8), actual))
        .map(Map.Entry::getKey)
        .findFirst();
  }
}
