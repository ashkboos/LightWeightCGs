package util;

import eu.fasten.core.data.opal.MavenCoordinate;
import evaluation.StatCounter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jooq.tools.csv.CSVReader;

public class CSVUtils {

    public static final String JAR = "jar";
    public static final String DEP_SEPERATOR = ";";
    public static final String COLUMN_SEPERATOR = ",";

    public static void writeToCSV( final List<String[]> data,
                                   final String resultPath){
        File csvOutputFile = new File(resultPath);
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            data.stream()
                .map(CSVUtils::convertToCSV)
                .forEach(pw::println);
            pw.flush();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static String convertToCSV(final String[] data) {
        return Stream.of(data)
            .map(CSVUtils::escapeSpecialCharacters)
            .collect(Collectors.joining(","));
    }

    
    public static String escapeSpecialCharacters( String data) {
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
    }

    
    public static Map<MavenCoordinate, List<MavenCoordinate>> readResolvedCSV(
         final String inputPath) {

        Map<MavenCoordinate, List<MavenCoordinate>> result = new HashMap<>();

        try (var csvReader = new CSVReader(new FileReader(inputPath), ',', '\'', 1)) {
            String[] values;
            while ((values = csvReader.readNext()) != null) {
                final var coords = converstStringToCoordList(values[2]);
                result.put(coords.get(0), coords);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    
    private static List<MavenCoordinate> converstStringToCoordList( final String coords) {
        List<MavenCoordinate> result = new ArrayList<>();
        for (final var coord : coords.split(DEP_SEPERATOR)) {
            result.add(MavenCoordinate.fromString(coord, JAR));
        }
        return result;
    }

    
    public static List<MavenCoordinate> getCoordinatesFromRow( final String row) {
        final var rowColumns = row.split(COLUMN_SEPERATOR);
        return converstStringToCoordList(rowColumns[2]);
    }

    
    public static List<Map<String, String>> readCSV( final String inputPath) {
        final List<Map<String, String>> result = new ArrayList<>();
        if (!new File(inputPath).exists()) {
            return result;
        }
            try (var csvReader = new CSVReader(new FileReader(inputPath), ',', '\'')) {
                String[] values;
                boolean firstRow = true;
                final List<String> header = new ArrayList<>();
                while ((values = csvReader.readNext()) != null) {
                    Map<String, String> row = new HashMap<>();
                    for (int i = 0; i < values.length; i++) {
                        String value = values[i];
                        if (firstRow) {
                            header.add(value);
                        } else {
                            row.put(header.get(i), value);
                        }
                    }
                    if (!firstRow) {
                        result.add(row);
                    }
                    firstRow = false;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        return result;
    }

    
    public static List<String> readCSVColumn( final String inputPath,
                                             final int columnNum) {
        List<String> result = new ArrayList<>();
        try (var csvReader = new CSVReader(new FileReader(inputPath))) {
            String[] values;
            while ((values = csvReader.readNext()) != null) {
                result.add(values[columnNum]);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    
    public static List<String> dropTheHeader( final List<String> csv) {
        csv.remove(0);
        return csv;
    }

    
    public static String[] getHeaderOf( final String CSVName) {
        if (CSVName.equals("Overall")) {
            return new String[] {"number", "coordinate", "opalTime",
                "cgPool", "mergeTime", "UCHTime",
                "opalNodes", "opalEdges", "mergeNodes", "mergeEdges", "appPCGTime",
                "cgSize", "mergerSize"};

        } else if (CSVName.equals("Generator")) {
            return new String[] {"number", "coordinate", "time",
                "nodes", "edges"};

        } else if (CSVName.equals("CGPool")) {
            return new String[] {"number", "coordinate", "occurrence", "isolatedRevisionTime",
                "nodes", "edges", "cgSize"};

        } else if (CSVName.equals("Accuracy")) {
            return new String[] {"number", "coordinate", "source", "precision", "recall",
                "OPAL", "Merge", "intersection"};

        } else if (CSVName.equals("Log")) {
            return new String[] {"number", "coordinate", "opalLog", "mergeLog"};
        }

        //Merge
        return new String[] {"number", "rootCoordinate", "artifact", "mergeTime", "uchTime",
            "nodes", "edges", "mergerSize"};

    }

    
    public static String[] getMergeContent(final int counter,  final MavenCoordinate rootCoord,
                                            final StatCounter.MergeTimer merge, final Long uchTime) {
        return new String[] {
            /* number */ String.valueOf(counter),
            /* rootCoordinate */ rootCoord.getCoordinate(),
            /* artifact */ String.valueOf(merge.artifact.getCoordinate()),
            /* mergeTime */ String.valueOf(merge.time),
            /* uchTime */ String.valueOf(uchTime),
            /* nodes */ String.valueOf(merge.mergeStats.nodes),
            /* edges */ String.valueOf(merge.mergeStats.edges),
            /* mergerSize */ String.valueOf((long) merge.mergerSize)
        };
    }

    
    public static List<String[]> buildDataCSVofResolvedCoords(
         final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData) {
        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(new String[] {"number", "coordinate", "dependencies"});
        int counter = 0;
        for (final var coorDeps : resolvedData.entrySet()) {
            final var coord = coorDeps.getKey();
            dataLines.add(new String[] {
                /* number */ String.valueOf(counter),
                /* coordinate */ coord.getCoordinate(),
                /* dependencies */ StatCounter.toString(coorDeps.getValue())});
            counter++;
        }
        return dataLines;
    }
}
