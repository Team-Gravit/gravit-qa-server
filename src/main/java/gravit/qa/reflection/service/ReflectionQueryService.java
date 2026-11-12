package gravit.qa.reflection.service;

import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.reflection.domain.ReflectionState;
import gravit.qa.reflection.dto.internal.SpecReflectionDto;
import gravit.qa.reflection.repository.SpecReflectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static gravit.qa.global.exception.domain.CustomErrorCode.REFLECTION_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class ReflectionQueryService {

    private final SpecReflectionRepository specReflectionRepository;

    @Transactional(readOnly = true)
    public List<SpecReflectionDto> getReflections(long sprintId) {
        return specReflectionRepository.findAllBySprintIdOrderByIdAsc(sprintId).stream()
                .map(SpecReflectionDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SpecReflectionDto getReflection(long reflectionId) {
        return specReflectionRepository.findById(reflectionId)
                .map(SpecReflectionDto::from)
                .orElseThrow(() -> new RestApiException(REFLECTION_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public long countUnresolved(long sprintId) {
        return specReflectionRepository.countBySprintIdAndStateIn(sprintId, ReflectionState.UNRESOLVED);
    }
}
