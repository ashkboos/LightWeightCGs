package evaluation;

import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import java.io.File;
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
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.tools.csv.CSVReader;

public class InputDemography {


    static List<String[]> buildCSV(Map<String, List<Integer>> result) {
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
}
