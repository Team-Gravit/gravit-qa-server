package gravit.qa.reflection.service;

import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.reflection.domain.ReflectionState;
import gravit.qa.reflection.domain.SpecReflection;
import gravit.qa.reflection.dto.internal.ReflectionProposalDto;
import gravit.qa.reflection.dto.internal.SpecReflectionDto;
import gravit.qa.reflection.repository.SpecReflectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static gravit.qa.global.exception.domain.CustomErrorCode.REFLECTION_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class ReflectionCommandService {

    private final SpecReflectionRepository specReflectionRepository;

    @Transactional
    public void save(
            long sprintId,
            long featureId,
            ReflectionProposalDto proposal
    ) {
        specReflectionRepository.deleteAllOfFeatureExcept(sprintId, featureId, ReflectionState.APPLIED);
        specReflectionRepository.save(SpecReflection.create(sprintId, featureId, proposal));
    }

    @Transactional
    public void deleteUnapplied(long sprintId) {
        specReflectionRepository.deleteAllExcept(sprintId, ReflectionState.APPLIED);
    }

    @Transactional
    public SpecReflectionDto startApply(
            long reflectionId,
            int version
    ) {
        SpecReflection reflection = findReflection(reflectionId);
        reflection.startApply(version);
        specReflectionRepository.flush();
        return SpecReflectionDto.from(reflection);
    }

    @Transactional
    public void markOperationApplied(
            long reflectionId,
            int index,
            String createdBlockId
    ) {
        findReflection(reflectionId).markOperationApplied(index, createdBlockId);
    }

    @Transactional
    public void complete(
            long reflectionId,
            long adminId,
            LocalDateTime now
    ) {
        findReflection(reflectionId).complete(adminId, now);
    }

    @Transactional
    public void fail(
            long reflectionId,
            String message,
            long adminId,
            LocalDateTime now
    ) {
        findReflection(reflectionId).fail(message, adminId, now);
    }

    @Transactional
    public void reject(
            long reflectionId,
            int version,
            long adminId,
            LocalDateTime now
    ) {
        findReflection(reflectionId).reject(version, adminId, now);
    }

    private SpecReflection findReflection(long reflectionId) {
        return specReflectionRepository.findById(reflectionId)
                .orElseThrow(() -> new RestApiException(REFLECTION_NOT_FOUND));
    }
}
