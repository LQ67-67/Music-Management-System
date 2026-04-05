package com.example.musiclibrary.util;

import com.example.musiclibrary.model.Track;
import javafx.scene.image.Image;

import java.io.InputStream;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class TrackMediaResolver {

    private static final String MUSIC_RESOURCE_DIR = "musics";
    private static final String TRACK_IMAGE_RESOURCE_DIR = "images/tracks";
    private static final String DEFAULT_IMAGE_RESOURCE = "/images/music.png";
    private static final List<String> AUDIO_EXTENSIONS = Arrays.asList(".mp3", ".wav", ".aac", ".m4a");
    private static final List<String> IMAGE_EXTENSIONS = Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".bmp");

    private TrackMediaResolver() {
    }

    public static List<String> listMusicFiles() {
        return listResourceFileNames(MUSIC_RESOURCE_DIR, AUDIO_EXTENSIONS);
    }

    public static URL findMusicUrl(String fileName) {
        URL resource = TrackMediaResolver.class.getResource("/" + MUSIC_RESOURCE_DIR + "/" + fileName);
        if (resource != null) {
            return resource;
        }

        Path fallback = Paths.get("src/main/resources", MUSIC_RESOURCE_DIR, fileName);
        if (Files.exists(fallback)) {
            try {
                return fallback.toFile().getAbsoluteFile().toURI().toURL();
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    public static Image loadTrackImage(Track track, String trackFileName) {
        List<String> imageFiles = listResourceFileNames(TRACK_IMAGE_RESOURCE_DIR, IMAGE_EXTENSIONS);
        String matchedFile = findBestImageFile(track, trackFileName, imageFiles);
        if (matchedFile != null) {
            Image image = loadImageFromResource(TRACK_IMAGE_RESOURCE_DIR + "/" + matchedFile);
            if (image != null) {
                return image;
            }
        }
        return loadImageFromResource(DEFAULT_IMAGE_RESOURCE.substring(1));
    }

    private static String findBestImageFile(Track track, String trackFileName, List<String> imageFiles) {
        if (imageFiles.isEmpty()) {
            return null;
        }

        List<String> stems = imageFiles.stream()
                .map(TrackMediaResolver::baseName)
                .collect(Collectors.toList());

        for (String candidate : buildCandidates(track, trackFileName)) {
            int index = stems.indexOf(candidate);
            if (index >= 0) {
                return imageFiles.get(index);
            }
        }

        for (String candidate : buildCandidates(track, trackFileName)) {
            String normalizedCandidate = normalize(candidate);
            for (int i = 0; i < stems.size(); i++) {
                String normalizedStem = normalize(stems.get(i));
                if (!normalizedCandidate.isBlank()
                        && !normalizedStem.isBlank()
                        && (normalizedStem.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedStem))) {
                    return imageFiles.get(i);
                }
            }
        }

        Optional<String> nearestNumericMatch = findNearestNumericMatch(trackFileName, imageFiles);
        return nearestNumericMatch.orElse(null);
    }

    private static Optional<String> findNearestNumericMatch(String trackFileName, List<String> imageFiles) {
        String trackBaseName = baseName(trackFileName);
        if (!trackBaseName.matches("\\d+")) {
            return Optional.empty();
        }

        long target = Long.parseLong(trackBaseName);
        return imageFiles.stream()
                .filter(name -> baseName(name).matches("\\d+"))
                .min(Comparator.comparingLong(name -> Math.abs(Long.parseLong(baseName(name)) - target)))
                .filter(name -> Math.abs(Long.parseLong(baseName(name)) - target) <= 2);
    }

    private static List<String> buildCandidates(Track track, String trackFileName) {
        List<String> candidates = new ArrayList<>();
        if (trackFileName != null && !trackFileName.isBlank()) {
            candidates.add(baseName(trackFileName));
        }
        if (track != null) {
            candidates.add(String.valueOf(track.getId()));
            addIfPresent(candidates, track.getTitle());
            addIfPresent(candidates, track.getAlbum());
            addIfPresent(candidates, track.getArtist());
        }
        return candidates.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toList());
    }

    private static void addIfPresent(List<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private static Image loadImageFromResource(String resourcePath) {
        InputStream stream = TrackMediaResolver.class.getResourceAsStream("/" + resourcePath);
        if (stream != null) {
            return new Image(stream);
        }

        Path fallback = Paths.get("src/main/resources", resourcePath);
        if (Files.exists(fallback)) {
            try {
                File file = fallback.toFile().getAbsoluteFile();
                return new Image(file.toURI().toString());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static List<String> listResourceFileNames(String resourceDir, List<String> extensions) {
        try {
            URL url = TrackMediaResolver.class.getClassLoader().getResource(resourceDir);
            if (url != null && "file".equalsIgnoreCase(url.getProtocol())) {
                try (var stream = Files.list(Paths.get(url.toURI()))) {
                    return stream
                            .filter(Files::isRegularFile)
                            .map(path -> path.getFileName().toString())
                            .filter(name -> hasAnyExtension(name, extensions))
                            .sorted()
                            .collect(Collectors.toList());
                }
            }
        } catch (Exception ignored) {
        }

        Path fallback = Paths.get("src/main/resources", resourceDir);
        if (Files.exists(fallback)) {
            try (var stream = Files.list(fallback)) {
                return stream
                        .filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> hasAnyExtension(name, extensions))
                        .sorted()
                        .collect(Collectors.toList());
            } catch (Exception ignored) {
            }
        }

        return List.of();
    }

    private static boolean hasAnyExtension(String fileName, List<String> extensions) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return extensions.stream().anyMatch(lower::endsWith);
    }

    private static String baseName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int slashIndex = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String simpleName = slashIndex >= 0 ? fileName.substring(slashIndex + 1) : fileName;
        int dotIndex = simpleName.lastIndexOf('.');
        return dotIndex > 0 ? simpleName.substring(0, dotIndex) : simpleName;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\p{IsHan}]+", "");
    }
}
