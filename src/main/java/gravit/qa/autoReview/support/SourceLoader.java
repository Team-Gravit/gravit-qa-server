package gravit.qa.autoReview.support;

import java.util.Optional;

@FunctionalInterface
public interface SourceLoader {

    Optional<String> load(String path);
}
