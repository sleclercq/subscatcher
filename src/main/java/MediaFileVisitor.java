import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import com.googlecode.opensubtitlesjapi.LANGUAGE;
import com.googlecode.opensubtitlesjapi.OpenSubtitlesAPI;
import com.googlecode.opensubtitlesjapi.OpenSubtitlesException;

/**
 *
 */
public class MediaFileVisitor extends SimpleFileVisitor<Path> {

    private static final List<String> VIDEO_FILE_EXTENSIONS = ImmutableList.copyOf(new String[] {
            "mpeg", "mpg", "mpe", "mpeg", "m1s", "mpa", "mp2", "m2a", "mp2v", "m2v", "m2s",
            "avi", "mov", "qt", "asf", "asx", "wmv", "wma", "wmx", "rm", "ra", "ram", "rmvb",
            "mp4", "3gp", "ogm", "mkv"
    });

    private static final List<String> SUBTITLES_EXTENSIONS = ImmutableList.copyOf(new String[] {
            "aqt", "jss", "sub", "ttxt", "sub", "pjs", "psb", "rt", "smi", "ssf", "srt", "gsub",
            "ssa", "ass", "sub", "usf", "sub", "idx", "stl"
    });

    private static final long OPENSUBTITLES_API_THROTTLE = 1000 * 10;

    private OpenSubtitlesAPI openSubtitlesAPI;
    private String openSubtitlesLogin;
    private String openSubtitlesPassword;
    private String token;
    private final Set<String> ignoredFolders;


    public MediaFileVisitor(Properties properties) {
        openSubtitlesAPI = new OpenSubtitlesAPI();
        openSubtitlesLogin = properties.getProperty("opensubtitles.login");
        openSubtitlesPassword = properties.getProperty("opensubtitles.password");
        this.ignoredFolders = Sets.newHashSet(
                Splitter
                .on(';')
                .omitEmptyStrings()
                .trimResults()
                .split(properties.getProperty("ignored.folders"))
        );
    }

    public void loginToOpenSubtitles() throws OpenSubtitlesException {
        token = openSubtitlesAPI.login(openSubtitlesLogin, openSubtitlesPassword);
        if (token == null) {
            throw new OpenSubtitlesException("Could not login, token null");
        }
        System.out.println("Logged on, token : " + token);
    }

    @Override
    public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
        if (Predicates.in(ignoredFolders).apply(dir.getFileName().toString())) {
            System.out.println("Ignoring folder " + dir.getFileName().toString());
            return FileVisitResult.SKIP_SUBTREE;
        } else {
            System.out.println("Processing folder " + dir.getFileName().toString());
            return FileVisitResult.CONTINUE;
        }
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Objects.requireNonNull(file);
        Objects.requireNonNull(attrs);
        if (isMedia(file)) {
            if (hasSubtitle(file)) {
                System.out.println("Subtitles exists for " + file);
            } else {
                System.out.println("Search subtitles for " + file);
                searchAndDownloadSubtitle(file);
            }
        }
        return FileVisitResult.CONTINUE;
    }

    private boolean isMedia(Path file) {
        for (String videoFileExtension : VIDEO_FILE_EXTENSIONS) {
            if (file.toString().endsWith(videoFileExtension)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSubtitle(Path file) {
        for (String subtitlesExtension : SUBTITLES_EXTENSIONS) {
            if (Files.exists(Paths.get(file.toAbsolutePath() + ".eng." + subtitlesExtension))) {
                return true;
            }
        }
        return false;
    }

    private void searchAndDownloadSubtitle(Path video) {
        try {
            Thread.sleep(OPENSUBTITLES_API_THROTTLE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            List<Map<String,Object>> searchResults = openSubtitlesAPI.search(token, video.toFile(), LANGUAGE.ENG);
            System.out.println("Number of result maps : " + searchResults.size());
            Optional<Map<String,Object>> firstResult = FluentIterable.from(searchResults).first();
            if (firstResult.isPresent()) {
                downloadSubtitle(video, firstResult.get());
            }
            System.out.println("Done!");
        } catch (IOException|OpenSubtitlesException e) {
            e.printStackTrace();
        }

    }

    private void downloadSubtitle(Path video, Map<String, Object> searchResult) throws OpenSubtitlesException, IOException {
        Optional<byte[]> subtitleZipBytes = downloadZipFromOpenSubtitles(searchResult);
        if (subtitleZipBytes.isPresent()) {
            saveSubtitleToDisk(video, (String) searchResult.get("SubFormat"), subtitleZipBytes.get());
        }
    }


    private Optional<byte[]> downloadZipFromOpenSubtitles(Map<String, Object> searchResult) throws OpenSubtitlesException {
        String subtitleId = (String) searchResult.get("IDSubtitleFile");
        String subtitleName = (String) searchResult.get("SubFileName");
        System.out.println("Downloading " + subtitleName);
        Map<String, byte[]> subtitleZips = openSubtitlesAPI.download(token, Integer.parseInt(subtitleId));
        System.out.println("Downloaded " + subtitleZips.size() + " items");
        return FluentIterable.from(subtitleZips.values()).first();
    }

    private void saveSubtitleToDisk(Path video, String subFormat, byte[] subtitleZipBytes) throws IOException {
        Path subtitlesTargetPath = Paths.get(video + ".eng." + subFormat);
        FileOutputStream fileOutputStream = new FileOutputStream(subtitlesTargetPath.toFile());
        fileOutputStream.write(subtitleZipBytes);
        fileOutputStream.close();
    }


    public void logoutFromOpenSubtitles() {
        try {
            openSubtitlesAPI.logout(token);
            System.out.println("Logged out.");
        } catch (OpenSubtitlesException e) {
            e.printStackTrace();
        }
    }

}
