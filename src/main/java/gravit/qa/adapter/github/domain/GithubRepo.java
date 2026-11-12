package gravit.qa.adapter.github.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GithubRepo {

    GRAVIT_SERVER("gravit-server"),
    GRAVIT_WEB("gravit-web"),
    GRAVIT_AOS("gravit-aos");

    private final String repoName;
}
