package gravit.qa.feature.service;

import gravit.qa.feature.domain.Feature;
import gravit.qa.feature.domain.SprintFeature;
import gravit.qa.feature.dto.internal.FeatureUpsertDto;
import gravit.qa.feature.dto.internal.SprintFeatureCreateDto;
import gravit.qa.feature.repository.FeatureRepository;
import gravit.qa.feature.repository.SprintFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FeatureCommandService {

    private final FeatureRepository featureRepository;
    private final SprintFeatureRepository sprintFeatureRepository;

    @Transactional
    public Map<String, Long> upsertAll(List<FeatureUpsertDto> features) {
        Map<String, Long> screenIdToFeatureId = new LinkedHashMap<>();
        for (FeatureUpsertDto feature : features) {
            Feature saved = featureRepository.findByNotionPageId(feature.notionPageId())
                    .or(() -> featureRepository.findByScreenId(feature.screenId()))
                    .map(existing -> {
                        existing.update(feature);
                        return existing;
                    })
                    .orElseGet(() -> featureRepository.save(Feature.create(feature)));
            screenIdToFeatureId.put(feature.screenId(), saved.getId());
        }
        featureRepository.flush();
        return screenIdToFeatureId;
    }

    @Transactional
    public void replaceSprintFeatures(
            long sprintId,
            List<SprintFeatureCreateDto> sprintFeatures
    ) {
        sprintFeatureRepository.deleteAllBySprintId(sprintId);
        sprintFeatureRepository.saveAll(sprintFeatures.stream()
                .map(sprintFeature -> SprintFeature.create(
                        sprintId,
                        sprintFeature.featureId(),
                        sprintFeature.figmaNodeIds(),
                        sprintFeature.warnings()
                ))
                .toList());
    }
}
