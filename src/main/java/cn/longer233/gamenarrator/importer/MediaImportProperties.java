package cn.longer233.gamenarrator.importer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "game-narrator.media-import")
public class MediaImportProperties {
    private String ytDlp = "./tools/yt-dlp/yt-dlp.exe";
    private boolean forceIpv4 = true;
    private boolean localAuthenticationDiscovery = false;
    private List<String> browserPriority = new ArrayList<>(List.of("edge", "chrome", "firefox"));
    private List<String> cookieSearchPaths = new ArrayList<>();
    private List<Platform> platforms = new ArrayList<>();

    public String getYtDlp() { return ytDlp; }
    public void setYtDlp(String ytDlp) { this.ytDlp = ytDlp; }
    public boolean isForceIpv4() { return forceIpv4; }
    public void setForceIpv4(boolean forceIpv4) { this.forceIpv4 = forceIpv4; }
    public boolean isLocalAuthenticationDiscovery() { return localAuthenticationDiscovery; }
    public void setLocalAuthenticationDiscovery(boolean localAuthenticationDiscovery) {
        this.localAuthenticationDiscovery = localAuthenticationDiscovery;
    }
    public List<String> getBrowserPriority() { return browserPriority; }
    public void setBrowserPriority(List<String> browserPriority) {
        this.browserPriority = browserPriority == null ? new ArrayList<>() : browserPriority;
    }
    public List<String> getCookieSearchPaths() { return cookieSearchPaths; }
    public void setCookieSearchPaths(List<String> cookieSearchPaths) {
        this.cookieSearchPaths = cookieSearchPaths == null ? new ArrayList<>() : cookieSearchPaths;
    }
    public List<Platform> getPlatforms() { return platforms; }
    public void setPlatforms(List<Platform> platforms) {
        this.platforms = platforms == null ? new ArrayList<>() : platforms;
    }

    public static class Platform {
        private String id;
        private List<String> hosts = new ArrayList<>();
        private List<String> cookieDomains = new ArrayList<>();
        private String referer;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public List<String> getHosts() { return hosts; }
        public void setHosts(List<String> hosts) { this.hosts = hosts; }
        public List<String> getCookieDomains() { return cookieDomains; }
        public void setCookieDomains(List<String> cookieDomains) { this.cookieDomains = cookieDomains; }
        public String getReferer() { return referer; }
        public void setReferer(String referer) { this.referer = referer; }

        public boolean matchesHost(String host) {
            return hosts.stream().anyMatch(suffix ->
                    host.equalsIgnoreCase(suffix)
                            || host.toLowerCase().endsWith("." + suffix.toLowerCase()));
        }

        public boolean acceptsCookieDomain(String domain) {
            String normalized = domain.startsWith(".") ? domain.substring(1) : domain;
            return cookieDomains.stream().anyMatch(suffix ->
                    normalized.equalsIgnoreCase(suffix)
                            || normalized.toLowerCase().endsWith("." + suffix.toLowerCase()));
        }
    }
}
