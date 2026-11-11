package gravit.qa.checklist.dto.internal;

public record StateCountDto(
        long total,
        long checked,
        long pass,
        long fail
) {

    public static StateCountDto of(
            long total,
            long checked,
            long pass,
            long fail
    ) {
        return new StateCountDto(total, checked, pass, fail);
    }
}
