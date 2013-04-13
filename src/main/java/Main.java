import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;

import com.googlecode.opensubtitlesjapi.LANGUAGE;
import com.googlecode.opensubtitlesjapi.OpenSubtitlesAPI;
import com.googlecode.opensubtitlesjapi.OpenSubtitlesException;
import com.sun.nio.zipfs.ZipPath;

/**
 *
 */
public class Main {

    private static final int A_LONG_TIME = 1000 * 60 * 60;

    public static void main(String[] args) {
        Properties prop = new Properties();

        try {
            InputStream inputStream = Main.class.getClassLoader().getResourceAsStream("config.properties");
            prop.load(inputStream);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        while(true) {
            MediaFileVisitor visitor = new MediaFileVisitor(prop);
            try {
                visitor.loginToOpenSubtitles();
                Files.walkFileTree(Paths.get(prop.getProperty("search.directory")), visitor);
            } catch (IOException|OpenSubtitlesException e) {
                e.printStackTrace();
            } finally {
                visitor.logoutFromOpenSubtitles();
            }
            try {
                Thread.sleep(A_LONG_TIME);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
