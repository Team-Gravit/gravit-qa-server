package gravit.qa.support;

import gravit.qa.adapter.support.Sleeper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingSleeper implements Sleeper {

    private final List<Duration> sleeps = new CopyOnWriteArrayList<>();
    private final TestClock testClock;

    public RecordingSleeper(TestClock testClock) {
        this.testClock = testClock;
    }

    @Override
    public void sleep(Duration duration) {
        sleeps.add(duration);
        testClock.advance(duration);
    }

    public List<Duration> sleeps() {
        return List.copyOf(sleeps);
    }

    public void reset() {
        sleeps.clear();
    }
}
