/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package util;

import static eu.fasten.core.utils.FilesUtils.getFileFromResources;

import data.ResultCG;
import eu.fasten.core.data.opal.MavenArtifactDownloader;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.data.utils.DirectedGraphDeserializer;
import eu.fasten.core.data.utils.DirectedGraphSerializer;
import eu.fasten.core.maven.utils.MavenUtilities;
import eu.fasten.core.merge.CallGraphUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;
import org.apache.commons.io.FileDeleteStrategy;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilesUtils {
    public static final String CG_JSON_FILE = "cg.json";
    public static final MavenCoordinate JAVA_8_COORD =
        new MavenCoordinate("java", "lang", "1.8.0", "jar");
    private static final Logger logger = LoggerFactory.getLogger(FilesUtils.class);
    public static final String RT_JAR_NAME = "RtJar-1.8.0-221.jar";
    public static final String PCG_DIR = "pcgs";

    public static File[] getFile(final File opalDir, final String fileName) {
        return opalDir.listFiles((dir, name) -> name.equals(fileName));
    }

    public static void forceDelete(final File file) throws IOException {
        FileDeleteStrategy.FORCE.delete(file);
    }

    public File[] downloadToDir(final List<MavenCoordinate> mavenCoordinates)
        throws IOException {
        return download(mavenCoordinates).stream().map(Pair::getValue)
            .toArray(File[]::new);
    }

    public static File download(final MavenCoordinate coord) {
        if (coord.equals(JAVA_8_COORD)) {
            return getRTJar();
        }

        final var tmpFolder = System.getProperty("java.io.tmpdir");
        final var destPath =
            tmpFolder + java.io.File.separator + coord.getCoordinate().replaceAll(":", "_") +
                ".jar";
        var coordinateFile = new File(destPath);
        if (!coordinateFile.exists()) {
            try {
            var tmp =
                new MavenArtifactDownloader(coord).downloadArtifact(
                    MavenUtilities.MAVEN_CENTRAL_REPO);
                coordinateFile = Files.move(tmp.toPath(), Path.of(destPath)).toFile();
                if (tmp.exists()) {
                    FilesUtils.forceDelete(tmp);
                }
            } catch (MissingArtifactException | IOException e) {
                logger.warn("Could not move or delete file!");
            }
        }

        return coordinateFile;
    }

    public static void writeCGToFile(final String path, final ResultCG cg) {
        try {
            CallGraphUtils.writeToFile(path,
                new DirectedGraphSerializer().graphToJson(cg.dg, cg.uris),
                File.separator + CG_JSON_FILE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeCGToFile(final String path, final String cgStr) {
        try {
            CallGraphUtils.writeToFile(path, cgStr);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ResultCG readCG(final File opalDir)
        throws IOException {
        ResultCG result = new ResultCG();
        final var opalFile = getFile(opalDir, CG_JSON_FILE);
        if (opalFile != null && opalFile.length != 0) {
            result = deserializeCGFile(opalFile[0]);
        }
        return result;
    }

    private static ResultCG deserializeCGFile(final File serializedCGFile) throws IOException {
        final var cg = Files.readString(serializedCGFile.toPath());
        return new ResultCG(new DirectedGraphDeserializer().jsonToGraph(cg));
    }

    public static File getDir(final File pckg, final String opal) {
        return getFile(pckg, opal)[0];
    }

    public static File[] getLogs(final File opalDir) {
        return getFile(opalDir, "log");
    }

    public static File extractJar(final File jarFile, final String destDirPath) {
        final JarFile jar;
        final File result = new File(destDirPath);
        result.mkdir();
        try {
            jar = new JarFile(jarFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        jar.stream().forEach(jarEntry -> {
            if (jarEntry.getName().endsWith(".jar")) {
                try {
                    java.io.File f =
                        new java.io.File(destDirPath + java.io.File.separator + jarEntry.getName());
                    if (jarEntry.isDirectory()) { // if its a directory, create it
                        f.mkdir();
                        return;
                    }
                    java.io.InputStream is =
                        jar.getInputStream(jarEntry); // get the input stream
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
                    while (is.available() > 0) {  // write contents of 'is' to 'fos'
                        fos.write(is.read());
                    }
                    fos.close();
                    is.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        return result;
    }

    public static File jar(final List<File> files) {
        final File resultFile;
        try {
            resultFile = Files.createTempFile("fasten", ".jar").toFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        JarFileCreator.createJarArchive(resultFile, files.toArray(File[]::new));

        return resultFile;
    }

    public static List<Pair<MavenCoordinate, File>> download(final List<MavenCoordinate> depSet) {
        final List<Pair<MavenCoordinate, File>> result = new ArrayList<>();

        for (final var coord : depSet) {

            final var coordinateFile = download(coord);

            if (coordinateFile == null) {
                continue;
            }
            result.add(Pair.of(coord, coordinateFile));
        }

        return result;
    }

    public static String readFromLast(final File file, final int lines) {
        List<String> result = new ArrayList<>();
        int readLines = 0;
        StringBuilder builder = new StringBuilder();
        RandomAccessFile randomAccessFile = null;
        try {
            randomAccessFile = new RandomAccessFile(file, "r");
            long fileLength = file.length() - 1;
            // Set the pointer at the last of the file
            randomAccessFile.seek(fileLength);
            for (long pointer = fileLength; pointer >= 0; pointer--) {
                randomAccessFile.seek(pointer);
                char c;
                // read from the last one char at the time
                c = (char) randomAccessFile.read();
                // break when end of the line
                if (c == '\n') {
                    readLines++;
                    if (readLines == lines) {
                        break;
                    }
                }
                builder.append(c);
            }
            // Since line is read from the last so it
            // is in reverse so use reverse method to make it right
            builder.reverse();
            result.add(builder.toString());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (randomAccessFile != null) {
                try {
                    randomAccessFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        Collections.reverse(result);
        return String.join("\n", result);
    }

    public static File getRTJar() {
        return getFileFromResources(RT_JAR_NAME);
    }
}
