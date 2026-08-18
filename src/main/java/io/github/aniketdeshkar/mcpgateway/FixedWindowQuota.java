package io.github.aniketdeshkar.mcpgateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class FixedWindowQuota implements Quota {
  private final int limit;
  private final Duration window;
  private final Clock clock;
  private final Map<String, Counter> counters = new HashMap<>();

  public FixedWindowQuota(int limit, Duration window, Clock clock) {
    if (limit <= 0 || window.isNegative() || window.isZero()) {
      throw new IllegalArgumentException("limit and window must be positive");
    }
    this.limit = limit;
    this.window = window;
    this.clock = clock;
  }

  @Override
  public synchronized boolean tryAcquire(String serverId) {
    Instant now = clock.instant();
    Counter current = counters.get(serverId);
    if (current == null || !now.isBefore(current.started().plus(window))) {
      counters.put(serverId, new Counter(now, 1));
      return true;
    }
    if (current.count() >= limit) {
      return false;
    }
    counters.put(serverId, new Counter(current.started(), current.count() + 1));
    return true;
  }

  private record Counter(Instant started, int count) {}
}
