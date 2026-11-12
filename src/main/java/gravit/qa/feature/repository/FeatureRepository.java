package gravit.qa.feature.repository;

import gravit.qa.feature.domain.Feature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FeatureRepository extends JpaRepository<Feature, Long> {

    Optional<Feature> findByNotionPageId(String notionPageId);

    Optional<Feature> findByScreenId(String screenId);

    List<Feature> findAllByIdIn(Collection<Long> ids);
}
