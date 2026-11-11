package gravit.qa.feature.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum SpecSection {

    SCREEN("화면에 보이는 것"),
    ACTION("사용자가 할 수 있는 것"),
    EXCEPTION("예외 상황");

    private final String title;

    public static Optional<SpecSection> fromTitle(String title) {
        return Arrays.stream(values())
                .filter(section -> section.title.equals(title))
                .findFirst();
    }
}
