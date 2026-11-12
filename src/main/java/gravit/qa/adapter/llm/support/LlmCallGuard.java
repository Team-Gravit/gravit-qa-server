package gravit.qa.adapter.llm.support;

import gravit.qa.adapter.llm.config.LlmProps;
import gravit.qa.adapter.support.Sleeper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class LlmCallGuard {

    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Deque<Instant> requestTimes = new ArrayDeque<>();

    private final Sleeper sleeper;

    private final Clock clock;
    private final LlmProps llmProps;

    public LlmCallGuard(
            Sleeper sleeper,
            Clock clock,
            LlmProps llmProps
    ) {
        this.sleeper = sleeper;
        this.clock = clock;
        this.llmProps = llmProps;
    }

    public <T> T execute(Supplier<T> request) {
        lock.lock();
        try {
            awaitRateLimit();
            requestTimes.addLast(clock.instant());
            return request.get();
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            requestTimes.clear();
        } finally {
            lock.unlock();
        }
    }

    private void awaitRateLimit() {
        evictExpired();
        while (requestTimes.size() >= llmProps.maxRequestsPerMinute()) {
            Duration wait = Duration.between(clock.instant(), requestTimes.getFirst().plus(RATE_WINDOW));
            if (wait.isPositive()) {
                sleeper.sleep(wait);
            }
            evictExpired();
        }
    }

    private void evictExpired() {
        Instant now = clock.instant();
        while (!requestTimes.isEmpty() && !requestTimes.getFirst().plus(RATE_WINDOW).isAfter(now)) {
            requestTimes.removeFirst();
        }
    }
}
