package gravit.qa.adapter.github.dto.internal;

public record AosVersionDto(
        int versionCode,

        String versionName,

        Integer previousVersionCode,

        boolean versionCodeWarning
) {
}
