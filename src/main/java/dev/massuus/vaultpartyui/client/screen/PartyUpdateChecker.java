package dev.massuus.vaultpartyui.client.screen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PartyUpdateChecker {
    private static final Pattern TAG_NAME_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static volatile boolean checkStarted;
    private static volatile boolean updateAvailable;

    private PartyUpdateChecker() {
    }

    static void ensureStarted(String currentVersion, String releasesApiUrl) {
        if (checkStarted) {
            return;
        }

        checkStarted = true;
        CompletableFuture.runAsync(() -> updateAvailable = isRemoteVersionNewer(currentVersion, releasesApiUrl));
    }

    static boolean isUpdateAvailable() {
        return updateAvailable;
    }

    private static boolean isRemoteVersionNewer(String currentVersion, String releasesApiUrl) {
        String latestVersion = fetchLatestReleaseTag(releasesApiUrl);
        if (latestVersion == null || latestVersion.isBlank()) {
            return false;
        }
        return compareVersions(latestVersion, currentVersion) > 0;
    }

    private static String fetchLatestReleaseTag(String releasesApiUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)new URL(releasesApiUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "VaultPartyUI");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);

            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                return null;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                Matcher matcher = TAG_NAME_PATTERN.matcher(response);
                return matcher.find() ? matcher.group(1) : null;
            }
        } catch (IOException ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static int compareVersions(String left, String right) {
        List<Integer> leftParts = numericVersionParts(left);
        List<Integer> rightParts = numericVersionParts(right);
        int size = Math.max(leftParts.size(), rightParts.size());
        for (int i = 0; i < size; i++) {
            int leftPart = i < leftParts.size() ? leftParts.get(i) : 0;
            int rightPart = i < rightParts.size() ? rightParts.get(i) : 0;
            int compare = Integer.compare(leftPart, rightPart);
            if (compare != 0) {
                return compare;
            }
        }
        return 0;
    }

    private static List<Integer> numericVersionParts(String version) {
        List<Integer> parts = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\d+").matcher(version == null ? "" : version.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            try {
                parts.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException ignored) {
                parts.add(0);
            }
        }
        return parts;
    }
}
