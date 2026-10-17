package gravit.qa.snapshot.infrastructure;

import gravit.qa.global.config.StorageProps;
import gravit.qa.snapshot.domain.SnapshotSource;
import gravit.qa.snapshot.dto.internal.StoredSnapshotDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class SnapshotFileStoreTest {

    private static final String HELLO_SHA256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    @TempDir
    private Path root;

    @Nested
    @DisplayName("스냅샷을 저장할 때")
    class 저장 {

        @Test
        void 스프린트와_출처별_경로에_쓰고_상대_경로와_sha256을_돌려준다() throws Exception {
            // given
            SnapshotFileStore store = new SnapshotFileStore(new StorageProps(root.toString()));

            // when
            StoredSnapshotDto stored = store.save(12L, 3L, SnapshotSource.NOTION_SPEC, "L.7.1.json", "hello");

            // then
            assertSoftly(softly -> {
                softly.assertThat(stored.storagePath()).isEqualTo("snapshots/12/3/NOTION_SPEC/L.7.1.json");
                softly.assertThat(stored.contentHash()).isEqualTo(HELLO_SHA256);
                softly.assertThat(stored.sizeBytes()).isEqualTo(5);
            });
            assertThat(Files.readString(root.resolve(stored.storagePath()))).isEqualTo("hello");
        }

        @Test
        void 같은_이름으로_다시_저장하면_덮어쓰고_임시_파일을_남기지_않는다() throws Exception {
            // given
            SnapshotFileStore store = new SnapshotFileStore(new StorageProps(root.toString()));
            store.save(12L, 3L, SnapshotSource.FIGMA_FRAME, "4-2.png", new byte[]{1, 2, 3});

            // when
            StoredSnapshotDto stored = store.save(12L, 3L, SnapshotSource.FIGMA_FRAME, "4-2.png", new byte[]{9});

            // then
            Path directory = root.resolve("snapshots/12/3/FIGMA_FRAME");
            assertThat(Files.readAllBytes(root.resolve(stored.storagePath()))).containsExactly(9);
            try (var files = Files.list(directory)) {
                assertThat(files.map(path -> path.getFileName().toString())).containsExactly("4-2.png");
            }
        }
    }

    @Nested
    @DisplayName("잘못된 입력이면")
    class 입력_검증 {

        @ParameterizedTest
        @ValueSource(strings = {"../escape.json", "a/b.json", "..", ".", ""})
        void 저장소_밖이나_하위_경로를_가리키는_파일_이름을_거부한다(String fileName) {
            // given
            SnapshotFileStore store = new SnapshotFileStore(new StorageProps(root.toString()));

            // when & then
            assertThatThrownBy(() -> store.save(12L, 3L, SnapshotSource.OPENAPI, fileName, "x"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 스프린트_ID가_양수가_아니면_거부한다() {
            // given
            SnapshotFileStore store = new SnapshotFileStore(new StorageProps(root.toString()));

            // when & then
            assertThatThrownBy(() -> store.save(0L, 3L, SnapshotSource.OPENAPI, "api-docs.json", "x"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
