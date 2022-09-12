package util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.jetbrains.annotations.NotNull;

public class JarFileCreator {
    public static int BUFFER_SIZE = 10240;
    public static void createJarArchive(final File archiveFile, final File[] tobeJared) {
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            // Open archive file
            FileOutputStream stream = new FileOutputStream(archiveFile);
            JarOutputStream out = new JarOutputStream(stream, new Manifest());

            for (File file : tobeJared) {
                if (file == null || !file.exists() || file.isDirectory())
                    continue;

                // Add archive entry
                JarEntry jarAdd = new JarEntry(file.getName());
                jarAdd.setTime(file.lastModified());
                out.putNextEntry(jarAdd);

                // Write file to archive
                FileInputStream in = new FileInputStream(file);
                while (true) {
                    int nRead = in.read(buffer, 0, buffer.length);
                    if (nRead <= 0)
                        break;
                    out.write(buffer, 0, nRead);
                }
                in.close();
            }

            out.close();
            stream.close();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}