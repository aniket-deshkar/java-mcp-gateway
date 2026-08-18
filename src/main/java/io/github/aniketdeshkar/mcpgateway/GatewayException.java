package io.github.aniketdeshkar.mcpgateway;

public final class GatewayException extends RuntimeException {
  private final Code code;

  public GatewayException(Code code, String message) {
    super(message);
    this.code = code;
  }

  public Code code() {
    return code;
  }

  public enum Code {
    UNKNOWN_TOOL,
    FORBIDDEN,
    QUOTA_EXCEEDED,
    SERVER_UNAVAILABLE,
    UPSTREAM_FAILURE
  }
}
