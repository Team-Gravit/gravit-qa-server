package gravit.qa.adapter.support;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ThreadSleeper implements Sleeper {

    @Override
    public void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("외부 호출 재시도 대기 중 인터럽트되었습니다.", e);
        }
    }
}
