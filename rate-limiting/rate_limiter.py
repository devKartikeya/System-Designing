import threading
import time
from dataclasses import dataclass


@dataclass
class Bucket:
    tokens: float
    last_refill: float
    last_seen: float


class TokenBucketLimiter:
    def __init__(
        self,
        capacity: int,
        refill_rate: float,
        idle_ttl: float = 3600,
        clock=time.monotonic,
    ):
        if capacity <= 0:
            raise ValueError("capacity must be positive")
        if refill_rate <= 0:
            raise ValueError("refill_rate must be positive")
        if idle_ttl <= 0:
            raise ValueError("idle_ttl must be positive")

        self.capacity = capacity
        self.refill_rate = refill_rate
        self.idle_ttl = idle_ttl
        self._clock = clock
        self._buckets = {}
        self._lock = threading.Lock()

    def allow(self, key: str, cost: float = 1):
        """
        Returns:
            (allowed, remaining_tokens, retry_after_seconds)
        """
        if not key:
            raise ValueError("key must not be empty")
        if cost <= 0 or cost > self.capacity:
            raise ValueError("cost must be > 0 and <= capacity")

        now = self._clock()

        with self._lock:
            bucket = self._buckets.get(key)

            if bucket is None:
                bucket = Bucket(
                    tokens=float(self.capacity),
                    last_refill=now,
                    last_seen=now,
                )
                self._buckets[key] = bucket

            elapsed = max(0.0, now - bucket.last_refill)
            bucket.tokens = min(
                float(self.capacity),
                bucket.tokens + elapsed * self.refill_rate,
            )
            bucket.last_refill = now
            bucket.last_seen = now

            if bucket.tokens >= cost:
                bucket.tokens -= cost
                return True, bucket.tokens, 0.0

            missing_tokens = cost - bucket.tokens
            retry_after = missing_tokens / self.refill_rate

            return False, bucket.tokens, retry_after

    def cleanup_expired(self):
        """Remove inactive keys. Returns the number removed."""
        cutoff = self._clock() - self.idle_ttl

        with self._lock:
            expired = [
                key
                for key, bucket in self._buckets.items()
                if bucket.last_seen < cutoff
            ]

            for key in expired:
                del self._buckets[key]

        return len(expired)

    @property
    def tracked_clients(self):
        with self._lock:
            return len(self._buckets)
