package cn.longer233.gamenarrator.asset;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "game-narrator.asset-library")
public class AssetLibraryProperties {
    private int featuredPageSize = 3;
    private List<FeaturedSearch> featuredSearches = new ArrayList<>();
    private QueryExpansion queryExpansion = new QueryExpansion();
    private List<String> providerPriority = new ArrayList<>(List.of(
            "BILIBILI", "DOUYIN", "USER_REFERENCE", "OPENVERSE", "WIKIMEDIA"));
    private List<DomesticSource> domesticSources = new ArrayList<>();

    public int getFeaturedPageSize() {
        return featuredPageSize;
    }

    public void setFeaturedPageSize(int featuredPageSize) {
        this.featuredPageSize = Math.max(1, Math.min(12, featuredPageSize));
    }

    public List<FeaturedSearch> getFeaturedSearches() {
        return featuredSearches;
    }

    public void setFeaturedSearches(List<FeaturedSearch> featuredSearches) {
        this.featuredSearches = featuredSearches == null ? new ArrayList<>() : featuredSearches;
    }

    public QueryExpansion getQueryExpansion() {
        return queryExpansion;
    }

    public void setQueryExpansion(QueryExpansion queryExpansion) {
        this.queryExpansion = queryExpansion == null ? new QueryExpansion() : queryExpansion;
    }

    public List<String> getProviderPriority() { return providerPriority; }
    public void setProviderPriority(List<String> value) {
        providerPriority = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
    public List<DomesticSource> getDomesticSources() { return domesticSources; }
    public void setDomesticSources(List<DomesticSource> value) {
        domesticSources = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    public static class DomesticSource {
        private String id;
        private String name;
        private String url;
        private String searchUrl;
        private String assetTypes;
        private String region = "DOMESTIC";
        private int priority = 100;
        private boolean enabled = true;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getSearchUrl() { return searchUrl; }
        public void setSearchUrl(String searchUrl) { this.searchUrl = searchUrl; }
        public String getAssetTypes() { return assetTypes; }
        public void setAssetTypes(String assetTypes) { this.assetTypes = assetTypes; }
        public String getRegion() { return region; }
        public void setRegion(String region) {
            this.region = region == null || region.isBlank() ? "DOMESTIC" : region.trim().toUpperCase();
        }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = Math.max(0, priority); }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class QueryExpansion {
        private List<Synonym> synonyms = new ArrayList<>();
        private int aiTimeoutSeconds = 2;
        private int cacheHours = 12;
        private int cacheMaxEntries = 256;

        public List<Synonym> getSynonyms() { return synonyms; }
        public void setSynonyms(List<Synonym> synonyms) {
            this.synonyms = synonyms == null ? new ArrayList<>() : new ArrayList<>(synonyms);
        }
        public int getAiTimeoutSeconds() { return aiTimeoutSeconds; }
        public void setAiTimeoutSeconds(int value) { aiTimeoutSeconds = Math.max(1, Math.min(10, value)); }
        public int getCacheHours() { return cacheHours; }
        public void setCacheHours(int value) { cacheHours = Math.max(1, Math.min(168, value)); }
        public int getCacheMaxEntries() { return cacheMaxEntries; }
        public void setCacheMaxEntries(int value) { cacheMaxEntries = Math.max(16, Math.min(4096, value)); }

        public static class Synonym {
            private String intent;
            private String terms;
            public String getIntent() { return intent; }
            public void setIntent(String intent) { this.intent = intent; }
            public String getTerms() { return terms; }
            public void setTerms(String terms) { this.terms = terms; }
        }
    }

    public static class FeaturedSearch {
        private String assetType;
        private String query;

        public String getAssetType() {
            return assetType;
        }

        public void setAssetType(String assetType) {
            this.assetType = assetType;
        }

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }
    }
}
