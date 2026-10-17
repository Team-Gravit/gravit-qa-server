package gravit.qa.persona.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum PersonaKey {

    PRE_ONBOARDING("pre-onboarding", "온보딩 전. 온보딩을 마치지 않았고 부속 데이터가 없다"),
    FRESH("fresh", "온보딩 직후, 학습 이력 0"),
    LEARNER("learner", "학습 진행 중. 최근 14일 제출 이력(정답과 오답), 오답노트, 북마크, 주간 기록 보유"),
    STREAK("streak", "연속 학습 N일. 연속 학습일과 일별 기록이 일치"),
    CHAPTER_ALMOST("chapter-almost", "챕터 마지막 레슨 직전. 특정 챕터의 마지막 레슨 1개만 남음"),
    SOCIAL("social", "팔로우 관계와 피드 보유(맞팔로우, 단방향 팔로워, 피드, 축하)"),
    LEAGUE_EDGE("league-edge", "리그 승급, 강등 경계의 LP와 리그 이력 보유"),
    MISSION("mission", "미션 완료 직전의 진행도"),
    INTERVIEWER("interviewer", "면접 이력 보유. 세션 상태별(IN_PROGRESS, GRADING, GRADING_FAILED, COMPLETED) 1건씩과 요약, 약점 데이터"),
    INQUIRER("inquirer", "관리자 답변이 달린 문의 보유"),
    WITHDRAWING("withdrawing", "탈퇴 유예 중(복구 흐름 검증용)"),
    SUSPENDED("suspended", "정지된 사용자"),
    ADMIN("admin", "관리자 권한 사용자");

    private final String key;
    private final String description;

    public static boolean isKnown(String key) {
        return Arrays.stream(values()).anyMatch(persona -> persona.key.equals(key));
    }
}
