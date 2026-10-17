package gravit.qa.checklist.domain;

import gravit.qa.feature.domain.Part;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CheckCellId implements Serializable {

    private Long itemId;
    private Part part;

    public static CheckCellId of(
            long itemId,
            Part part
    ) {
        return new CheckCellId(itemId, part);
    }
}
