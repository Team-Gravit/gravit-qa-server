package gravit.qa.feature.service;

import gravit.qa.feature.domain.Feature;
import gravit.qa.feature.domain.SprintFeature;
import gravit.qa.feature.dto.internal.FeatureUpsertDto;
import gravit.qa.feature.dto.internal.SprintFeatureSpecDto;
import gravit.qa.feature.repository.FeatureRepository;
import gravit.qa.feature.repository.SprintFeatureRepository;
import gravit.qa.global.exception.domain.RestApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static gravit.qa.global.exception.domain.CustomErrorCode.CHECKLIST_FEATURE_NOT_IN_SPRINT;

@Service
@RequiredArgsConstructor
public class FeatureCommandService {

    private final FeatureRepository featureRepository;
    private final SprintFeatureRepository sprintFeatureRepository;

    @Transactional
    public Map<String, Long> upsertAll(List<FeatureUpsertDto> features) {
        Map<String, Long> screenIdToFeatureId = new LinkedHashMap<>();
        for (FeatureUpsertDto feature : features) {
            screenIdToFeatureId.put(feature.screenId(), upsert(feature));
        }
        featureRepository.flush();
        return screenIdToFeatureId;
    }

    @Transactional
    public void replaceSprintFeatures(
            long sprintId,
            List<SprintFeatureSpecDto> specs
    ) {
        sprintFeatureRepository.deleteAllBySprintId(sprintId);
        sprintFeatureRepository.saveAll(specs.stream()
                .map(spec -> SprintFeature.create(sprintId, spec))
                .toList());
    }

    @Transactional
    public void updateSpec(
            long sprintId,
            FeatureUpsertDto feature,
            SprintFeatureSpecDto spec
    ) {
        upsert(feature);
        findSprintFeature(sprintId, spec.featureId()).updateSpec(spec);
    }

    @Transactional
    public void updateFigmaNodeIds(
            long sprintId,
            long featureId,
            List<String> figmaNodeIds
    ) {
        findSprintFeature(sprintId, featureId).updateFigmaNodeIds(figmaNodeIds);
    }

    private long upsert(FeatureUpsertDto feature) {
        Feature saved = featureRepository.findByNotionPageId(feature.notionPageId())
                .or(() -> featureRepository.findByScreenId(feature.screenId()))
                .map(existing -> {
                    existing.update(feature);
                    return existing;
                })
                .orElseGet(() -> featureRepository.save(Feature.create(feature)));
        return saved.getId();
    }

    private SprintFeature findSprintFeature(
            long sprintId,
            long featureId
    ) {
        return sprintFeatureRepository.findBySprintIdAndFeatureId(sprintId, featureId)
                .orElseThrow(() -> new RestApiException(CHECKLIST_FEATURE_NOT_IN_SPRINT));
    }
}
