package evaluation;

import eu.fasten.core.data.opal.MavenCoordinate;
import java.io.File;
import java.util.List;
import java.util.Map;
import util.CSVUtils;
import util.FilesUtils;

public class StatCounterDeserializer {

    public static final int DEFAULT_FAILED = 0;

    static void updateFromFile(MavenCoordinate rootCoord,
                               final File opalDir,
                               final File mergeDir,
                               final StatCounter statCounter) {

        final var resultOpal = CSVUtils.readCSV(opalDir.getAbsolutePath() + "/result.csv");
        addGeneratorToStatCounter(rootCoord, resultOpal, statCounter);

        final var resultMerge = CSVUtils.readCSV(mergeDir.getAbsolutePath() + "/Merge.csv");
        addMergeToStatCounter(rootCoord, resultMerge, statCounter);

        final var cgPool = CSVUtils.readCSV(mergeDir.getAbsolutePath() + "/CGPool.csv");
        addCGPoolToStatCounter(rootCoord, cgPool, statCounter);

        statCounter.addLog(FilesUtils.getLogs(opalDir), FilesUtils.getLogs(mergeDir),
            opalDir.getPath());

    }


    private static void addGeneratorToStatCounter(
        MavenCoordinate root, final List<Map<String, String>> resultOpal,
        StatCounter statCounter) {
        final StatCounter.GeneratorStats opalStats;
        if (resultOpal.isEmpty()) {
            opalStats = new StatCounter.GeneratorStats((long) DEFAULT_FAILED,
                new StatCounter.GraphStats(DEFAULT_FAILED, DEFAULT_FAILED));
        } else {
            final var row = resultOpal.get(0);
            opalStats = new StatCounter.GeneratorStats(Long.parseLong(row.get("time")),
                new StatCounter.GraphStats(Integer.parseInt(row.get("nodes")),
                    Integer.parseInt(row.get("edges"))));
        }
        statCounter.addGenerator(root, opalStats);
    }


    private static void addMergeToStatCounter(
        MavenCoordinate rootCoord, final List<Map<String, String>> resultMerge,
        StatCounter statCounter) {
        if (resultMerge.isEmpty()) {
            statCounter.addMerge(rootCoord, DEFAULT_FAILED,
                new StatCounter.GraphStats(DEFAULT_FAILED, DEFAULT_FAILED));
            statCounter.addUCH(rootCoord, (long) DEFAULT_FAILED);
            return;
        }
        final var row = resultMerge.get(0);
        statCounter.addMerge(rootCoord, Long.parseLong(row.get("mergeTime")),
            new StatCounter.GraphStats(Integer.parseInt(row.get("nodes")),
                Integer.parseInt(row.get("edges"))));
        statCounter.addUCH(rootCoord, Long.parseLong(row.get("uchTime")));
    }

    private static void addCGPoolToStatCounter(MavenCoordinate rootCoord,
                                               final List<Map<String, String>> cgPool,
                                               final StatCounter statCounter) {
        if (cgPool.isEmpty()) {
            statCounter.addNewCGtoPool(rootCoord, DEFAULT_FAILED,
                new StatCounter.GraphStats(DEFAULT_FAILED, DEFAULT_FAILED));
            return;
        }
        for (final var cg : cgPool) {
            statCounter.addNewCGtoPool(MavenCoordinate.fromString(cg.get("coordinate"), "jar"),
                Long.parseLong(cg.get("isolatedRevisionTime")),
                new StatCounter.GraphStats(Integer.parseInt(cg.get("nodes")),
                    Integer.parseInt(cg.get("edges"))));
        }
    }
}
