package util;

import eu.fasten.core.data.opal.MavenCoordinate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import me.tongfei.progressbar.ProgressBar;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InputDataUtils {
    private static final Logger logger = LoggerFactory.getLogger(InputDataUtils.class);

    public static void resolveDependencies(final String input, final String output) {
        final var resolvedData =
            resolveAll(CSVUtils.dropTheHeader(CSVUtils.readCSVColumn(input, 0)));
        CSVUtils.writeToCSV(CSVUtils.buildDataCSVofResolvedCoords(resolvedData), output);
        logger.info("Wrote resolved data into file successfully!");
    }

    public static void splitInput(final String inputPath, final String chunkNum,
                                  final String resultPath) {
        final var data = CSVUtils.readResolvedCSV(inputPath);
        final var chunks = splitToChunks(data, Integer.parseInt(chunkNum));
        for (int i = 0; i < chunks.size(); i++) {
            final var part = chunks.get(i);
            CSVUtils.writeToCSV(
                CSVUtils.buildDataCSVofResolvedCoords(part), resultPath + "/chunk.p" + i + ".csv");
        }
        logger.info("Wrote data chunks into file successfully!");
    }

    private static List<Map<MavenCoordinate, List<MavenCoordinate>>> splitToChunks(
        final Map<MavenCoordinate, List<MavenCoordinate>> data, final int chunkNum) {
        final List<Map<MavenCoordinate, List<MavenCoordinate>>> result = new ArrayList<>();
        final var chunkSize = data.size() / chunkNum;
        int counter = 0;
        Map<MavenCoordinate, List<MavenCoordinate>> chunk = new HashMap<>();
        for (final var entry : data.entrySet()) {
            chunk.put(entry.getKey(), entry.getValue());
            counter++;
            if (counter % chunkSize == 0 && (data.size() - counter) > chunkNum) {
                result.add(chunk);
                chunk = new HashMap<>();
            } else if (data.size() == counter) {
                result.add(chunk);
            }
        }
        return result;
    }

    public static Map<MavenCoordinate, List<MavenCoordinate>> resolveAll(
        final List<String> dataSet) {

        ProgressBar pb = new ProgressBar("Resolving", dataSet.size());
        pb.start();

        final var result = new HashMap<MavenCoordinate, List<MavenCoordinate>>();
        for (final var coord : dataSet) {
            final var deps = resolve(coord);
            if (deps.isEmpty()) {
                continue;
            }
            result.put(
                MavenCoordinate.fromString(coord, deps.get(0).getPackaging().getClassifier()),
                convertToFastenCoordinates(deps)
            );
            pb.step();
        }
        pb.stop();
        return result;
    }

    private static List<org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate> resolve(
        final String row) {
        List<org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate> result =
            new ArrayList<>();
        try {
            result = Maven.resolver().resolve(row).withTransitivity()
                .asList(org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate.class);
        } catch (Exception e) {
            logger.warn("Exception occurred while resolving {}, {}", row, e);
            System.out.println(e);
        }
        return result;
    }

    private static List<MavenCoordinate> convertToFastenCoordinates(
        final List<org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate> revisions) {
        return revisions.stream().map(InputDataUtils::convertToFastenCoordinate)
            .collect(Collectors.toList());
    }

    public static List<MavenCoordinate> getDepsOnly(final List<MavenCoordinate> depSet) {
        List<MavenCoordinate> result = new ArrayList<>();
        for (int i = 0; i < depSet.size(); i++) {
            if (i == 0) {
                continue;
            }
            result.add(depSet.get(i));
        }
        return result;
    }

    private static MavenCoordinate convertToFastenCoordinate(
        final org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate revision) {
        return new MavenCoordinate(revision.getGroupId(),
            revision.getArtifactId(), revision.getVersion(),
            revision.getPackaging().getClassifier());
    }
}
