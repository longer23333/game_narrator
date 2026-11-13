package cn.longer233.gamenarrator.asset;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class AssetLicensePolicy {
    private static final Set<String> AUTOMATIC_LICENSES = Set.of(
            "cc0", "pdm", "by", "by-sa", "pexels_license", "pixabay_content_license");

    private AssetLicensePolicy() { }

    public static String normalize(String value) {
        String license = Objects.toString(value, "").trim().toLowerCase(Locale.ROOT);
        if (license.matches("cc-by(?:-sa)?-[1-4]\\.[0-9]")) {
            return license.substring(3).replaceFirst("-[1-4]\\.[0-9]$", "");
        }
        if (license.matches("cc by(?:-sa)?(?: [1-4]\\.[0-9])?")) {
            return license.substring(3).replaceFirst(" [1-4]\\.[0-9]$", "");
        }
        if (license.matches("cc0(?: 1\\.0)?")) return "cc0";
        if (license.equals("public domain")) return "pdm";
        return license;
    }

    public static boolean allowsAutomaticUse(String license) {
        return AUTOMATIC_LICENSES.contains(normalize(license));
    }

    public static boolean allowsUse(String provider, String license) {
        if (Set.of("LOCAL_UPLOAD", "LOCAL_DERIVED", "PROJECT")
                .contains(Objects.toString(provider, "").toUpperCase(Locale.ROOT))) return true;
        return allowsAutomaticUse(license);
    }

    public static boolean matchesConditions(String license, boolean commercial, boolean modification) {
        String normalized = normalize(license);
        if (!commercial && !modification) return true;
        if (allowsAutomaticUse(normalized)) return true;
        if (normalized.isBlank() || Set.of("unknown", "unlicensed", "rights_review_required").contains(normalized)) return false;
        if (!normalized.matches("(?:cc[ -])?by(?:-nc)?(?:-sa|-nd)?(?:[ -][1-4]\\.[0-9])?")) return false;
        return (!commercial || !normalized.contains("nc")) && (!modification || !normalized.contains("nd"));
    }
}
