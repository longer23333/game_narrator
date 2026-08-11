package cn.longer233.gamenarrator.asset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetProviderCredentialControllerTest {
    @Test
    void connectionTestUsesTheCurrentAccountsEffectiveKey() {
        AssetProviderCredentialService credentials = mock(AssetProviderCredentialService.class);
        PexelsAssetClient pexels = mock(PexelsAssetClient.class);
        PixabayAssetClient pixabay = mock(PixabayAssetClient.class);
        when(credentials.effectiveKey("PEXELS")).thenReturn("account-key");
        AssetSearchRequest expected = new AssetSearchRequest("game", "IMAGE", 1, 1,
                true, true, "PEXELS", "RELEVANCE");
        var response = new ObjectMapper().createObjectNode();
        response.putArray("results").addObject();
        when(pexels.search(eq(expected), eq("account-key"))).thenReturn(response);
        AssetProviderCredentialController controller = new AssetProviderCredentialController(
                credentials, pexels, pixabay);

        var result = controller.test("pexels");

        assertThat(result).containsEntry("success", true).containsEntry("resultCount", 1);
        verify(pexels).search(expected, "account-key");
    }
}
