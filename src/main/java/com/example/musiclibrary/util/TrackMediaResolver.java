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
import java.util.List;

public final class TrackMediaResolver {

    private static final String MUSIC_RESOURCE_DIR = "musics";
    private static final String TRACK_IMAGE_RESOURCE_DIR = "images/tracks";
    private static final String DEFAULT_IMAGE_RESOURCE = "/images/music.jpeg";
    private static final List<String> AUDIO_EXTENSIONS = Arrays.asList(".mp3", ".wav", ".aac", ".m4a");
    private static final List<String> IMAGE_EXTENSIONS = Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".bmp");

    private TrackMediaResolver() {
    }

    // list all music files in the musics directory
    public static List<String> listMusicFiles() {
        return listResourceFileNames(MUSIC_RESOURCE_DIR, AUDIO_EXTENSIONS);
    }

    // find music file URL by filename
    public static URL findMusicUrl(String fileName) {
        // load from resources
        URL resource = TrackMediaResolver.class.getResource("/" + MUSIC_RESOURCE_DIR + "/" + fileName);
        if (resource != null) {
            return resource;
        }

        // fallback to file system
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

    // load track image
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

    // find best matching image file for track
    private static String findBestImageFile(Track track, String trackFileName, List<String> imageFiles) {
        if (imageFiles.isEmpty()) {
            return null;
        }

        // extract base names from image files
        List<String> stems = new ArrayList<>();
        for (String file : imageFiles) {
            stems.add(baseName(file));
        }

        // build candidate names
        List<String> candidates = new ArrayList<>();
        if (trackFileName != null && !trackFileName.isEmpty()) {
            candidates.add(baseName(trackFileName));
        }
        if (track != null) {
            candidates.add(String.valueOf(track.getId()));
            if (track.getTitle() != null && !track.getTitle().isEmpty()) {
                candidates.add(track.getTitle());
            }
            if (track.getAlbum() != null && !track.getAlbum().isEmpty()) {
                candidates.add(track.getAlbum());
            }
            if (track.getArtist() != null && !track.getArtist().isEmpty()) {
                candidates.add(track.getArtist());
            }
        }

        // exact match first
        for (String candidate : candidates) {
            int index = stems.indexOf(candidate);
            if (index >= 0) {
                return imageFiles.get(index);
            }
        }

        // partial match
        for (String candidate : candidates) {
            String normalizedCandidate = candidate.toLowerCase().replaceAll("[^a-z0-9]+", "");
            for (int i = 0; i < stems.size(); i++) {
                String normalizedStem = stems.get(i).toLowerCase().replaceAll("[^a-z0-9]+", "");
                if (!normalizedCandidate.isEmpty() && !normalizedStem.isEmpty()) {
                    if (normalizedStem.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedStem)) {
                        return imageFiles.get(i);
                    }
                }
            }
        }

        // numeric match
        String trackBaseName = baseName(trackFileName);
        if (trackBaseName.matches("\\d+")) {
            long target = Long.parseLong(trackBaseName);
            for (String imageFile : imageFiles) {
                String imageBaseName = baseName(imageFile);
                if (imageBaseName.matches("\\d+")) {
                    long imageNum = Long.parseLong(imageBaseName);
                    if (Math.abs(imageNum - target) <= 2) {
                        return imageFile;
                    }
                }
            }
        }

        return null;
    }

    // load image from resource path
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

    // list all files in resource directory with specific extensions
    private static List<String> listResourceFileNames(String resourceDir, List<String> extensions) {
        try {
            URL url = TrackMediaResolver.class.getClassLoader().getResource(resourceDir);
            if (url != null && "file".equalsIgnoreCase(url.getProtocol())) {
                Path path = Paths.get(url.toURI());
                if (Files.isDirectory(path)) {
                    List<String> files = new ArrayList<>();
                    try (var stream = Files.list(path)) {
                        stream.forEach(p -> {
                            if (Files.isRegularFile(p)) {
                                String name = p.getFileName().toString();
                                if (hasAnyExtension(name, extensions)) {
                                    files.add(name);
                                }
                            }
                        });
                    }
                    files.sort(TrackMediaResolver::naturalCompare);
                    return files;
                }
            }
        } catch (Exception ignored) {
        }

        // fallback to file system
        Path fallback = Paths.get("src/main/resources", resourceDir);
        if (Files.exists(fallback)) {
            try {
                List<String> files = new ArrayList<>();
                try (var stream = Files.list(fallback)) {
                    stream.forEach(p -> {
                        if (Files.isRegularFile(p)) {
                            String name = p.getFileName().toString();
                            if (hasAnyExtension(name, extensions)) {
                                files.add(name);
                            }
                        }
                    });
                }
                files.sort(TrackMediaResolver::naturalCompare);
                return files;
            } catch (Exception ignored) {
            }
        }

        return new ArrayList<>();
    }

    // check if filename has any of the given extensions
    private static boolean hasAnyExtension(String fileName, List<String> extensions) {
        String lower = fileName.toLowerCase();
        for (String ext : extensions) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    // get base name without extension
    private static String baseName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int slashIndex = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String simpleName = slashIndex >= 0 ? fileName.substring(slashIndex + 1) : fileName;
        int dotIndex = simpleName.lastIndexOf('.');
        return dotIndex > 0 ? simpleName.substring(0, dotIndex) : simpleName;
    }

    // natural comparison for file names (sort by numeric value when applicable)
    private static int naturalCompare(String s1, String s2) {
        if (s1 == null && s2 == null) return 0;
        if (s1 == null) return -1;
        if (s2 == null) return 1;

        String name1 = baseName(s1);
        String name2 = baseName(s2);

        // try to parse as numbers
        try {
            Long num1 = Long.parseLong(name1);
            Long num2 = Long.parseLong(name2);
            return num1.compareTo(num2);
        } catch (NumberFormatException e) {
            // fall back to string comparison
            return name1.compareTo(name2);
        }
    }
}