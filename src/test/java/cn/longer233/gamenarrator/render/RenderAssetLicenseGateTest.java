package cn.longer233.gamenarrator.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RenderAssetLicenseGateTest {
    @Test
    void acceptsLocalUserAssetsAndConfirmedExternalLicenses() {
        assertThat(RenderAssetResolver.licenseRenderable("LOCAL_UPLOAD", "USER_AUTHORIZED")).isTrue();
        assertThat(RenderAssetResolver.licenseRenderable("OPENVERSE", "CC-BY-4.0")).isTrue();
    }

    @Test
    void rejectsExternalAssetsAwaitingRightsReviewOrWithoutLicense() {
        assertThat(RenderAssetResolver.licenseRenderable("BILIBILI", "RIGHTS_REVIEW_REQUIRED")).isFalse();
        assertThat(RenderAssetResolver.licenseRenderable("REMOTE", "UNKNOWN")).isFalse();
        assertThat(RenderAssetResolver.licenseRenderable("REMOTE", null)).isFalse();
        assertThat(RenderAssetResolver.licenseRenderable("REMOTE", "unknown")).isFalse();
        assertThat(RenderAssetResolver.licenseRenderable("REMOTE", "CC BY-NC 4.0")).isFalse();
        assertThat(RenderAssetResolver.licenseRenderable("REMOTE", "ARR")).isFalse();
    }
}
