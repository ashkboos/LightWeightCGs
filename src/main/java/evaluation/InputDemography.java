package evaluation;

import eu.fasten.core.data.opal.MavenArtifactDownloader;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.maven.utils.MavenUtilities;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jetbrains.annotations.NotNull;
import org.jooq.tools.csv.CSVReader;
import util.CSVUtils;
import util.FilesUtils;

public class InputDemography {

    static List<String[]> buildCSV(final Map<String, List<Integer>> result) {
        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(new String[] {"number", "coordinate", "depNum", "numFiles",
            "numFilesWithDeps"});
        int counter = 0;
        for (final var coorDeps : result.entrySet()) {
            final var coord = coorDeps.getKey();
            dataLines.add(new String[] {
                /* number */ String.valueOf(counter),
                /* coordinate */ coord,
                /* depNum */ String.valueOf(coorDeps.getValue().get(0)),
                /* numFiles*/ String.valueOf(coorDeps.getValue().get(1)),
                /* numFilesWithDeps*/ String.valueOf(coorDeps.getValue().get(2))});
            counter++;
        }
        return dataLines;
    }


    public static Map<MavenCoordinate, List<MavenCoordinate>> readResolvedDataCSV(
        final String inputPath) throws IOException {

        Map<MavenCoordinate, List<MavenCoordinate>> result = new HashMap<>();

        try (var csvReader = new CSVReader(new FileReader(inputPath), ',', '\'', 1)) {
            String[] values;
            while ((values = csvReader.readNext()) != null) {

                final var coords = getCoordsList(values[2]);
                result.put(coords.get(0), coords);
            }
        }
        return result;
    }


    private static List<MavenCoordinate> getCoordsList(final String coords) {
        List<MavenCoordinate> result = new ArrayList<>();

        var coordinates = coords.split(";");
        for (final var coord : coordinates) {
            result.add(MavenCoordinate.fromString(coord, "jar"));
        }
        return result;
    }

    public static void inputDemography(final String inputPath, final String outputPath) {
        final var data = CSVUtils.readResolvedCSV(inputPath);
        Map<String, List<Integer>> result = new HashMap<>();
        int counter = 0;
        for (final var entry : data.entrySet()) {
            int fileCounter = 0;
            int withoutDeps = 0;
            List<MavenCoordinate> value = entry.getValue();
            for (int i = 0; i < value.size(); i++) {
                MavenCoordinate coord = value.get(i);
                try {
                    final var file = new MavenArtifactDownloader(coord).downloadArtifact(
                        MavenUtilities.MAVEN_CENTRAL_REPO);

                    ZipInputStream is =
                        new ZipInputStream(new FileInputStream(file.getAbsolutePath()));
                    ZipEntry ze;
                    while ((ze = is.getNextEntry()) != null) {
                        if (!ze.isDirectory()) {
                            fileCounter++;
                        }
                    }
                    if (i == 0) {
                        withoutDeps = fileCounter;
                    }
                    is.close();
                    FilesUtils.forceDelete(file);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            System.out.println("\n\n ################################ Coord number : " + counter);
            counter++;
            result.put(entry.getKey().getCoordinate(), Arrays.asList(entry.getValue().size(),
                withoutDeps, fileCounter));
        }
        CSVUtils.writeToCSV(buildCSV(result), outputPath);
    }
}
