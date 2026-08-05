package cn.longer233.gamenarrator.script;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ManualNarrationValidationTest {
    @Test
    void allowsEmptyNarrationForOriginalAudioOrSilentManualEdits() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new UpdateScriptSegmentRequest("", "仅字幕", ""))).isEmpty();
            assertThat(validator.validate(new UpdateStoryboardSegmentRequest(
                    0, 2, "", "仅字幕", "", false, false))).isEmpty();
        }
    }

    @Test
    void stillRejectsMissingNarrationField() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(
                    new UpdateScriptSegmentRequest(null, "字幕", ""));
            assertThat(violations).extracting(item -> item.getPropertyPath().toString())
                    .containsExactly("narration");
        }
    }
}
