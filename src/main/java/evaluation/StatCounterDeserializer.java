package evaluation;

import data.InputDataRow;
import eu.fasten.core.data.opal.MavenCoordinate;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import util.CSVUtils;
import util.FilesUtils;

public class StatCounterDeserializer {
    @NotNull
    static InputDataRow updateFromFile(@NotNull final File opalDir,
                                       @NotNull final File mergeDir,
                                       @NotNull final StatCounter statCounter) {

        InputDataRow result = InputDataRow.initEmptyInputDataRow();
        final var resultOpal = CSVUtils.readCSV(opalDir.getAbsolutePath() + "/resultOpal.csv");
        if (!resultOpal.isEmpty()) result = addGeneratorToStatCounter(resultOpal.get(0), statCounter);

        InputDataRow merge = InputDataRow.initEmptyInputDataRow();
        final var resultMerge = CSVUtils.readCSV(mergeDir.getAbsolutePath() + "/Merge.csv");
        if (!resultMerge.isEmpty()) merge = addMergeToStatCounter(resultMerge.get(0), statCounter);

        final var cgPool = CSVUtils.readCSV(mergeDir.getAbsolutePath() + "/CGPool.csv");
        if (!cgPool.isEmpty()) addCGPoolToStatCounter(cgPool, statCounter);

        statCounter.addLog(FilesUtils.getLogs(opalDir), FilesUtils.getLogs(mergeDir), opalDir.getPath());

        if (result.isEmpty()) result = merge;

        return result;
    }

    @NotNull
    private static InputDataRow addGeneratorToStatCounter(
        @NotNull final Map<String, String> resultOpal,
        @NotNull StatCounter statCounter) {
        var result = InputDataRow.initEmptyInputDataRow();
        result.root = MavenCoordinate.fromString(resultOpal.get("coordinate"), "jar");
        final var opalStats = new StatCounter.GeneratorStats(Long.parseLong(resultOpal.get("time")),
            new StatCounter.GraphStats(Integer.parseInt(resultOpal.get("nodes")),
                Integer.parseInt(resultOpal.get("edges"))));

        statCounter.addOPAL(result.root, opalStats);
        for (final var dep : resultOpal.get("dependencies").split(";")) {
            result.addToDepSet(dep);
        }
        return result;
    }

    @NotNull
    private static InputDataRow addMergeToStatCounter(
        @NotNull final Map<String, String> resultMerge,
        @NotNull StatCounter statCounter) {

        InputDataRow inputDataRow = InputDataRow.initEmptyInputDataRow();
        for (final var dep : resultMerge.get("dependencies").split(";")) {
            inputDataRow.addToDepSet(dep);
        }

        inputDataRow.addRoot(resultMerge.get("rootCoordinate"));

        statCounter.addMerge(inputDataRow.root,
            MavenCoordinate.fromString(resultMerge.get("artifact"), "jar"),
            inputDataRow.deps, Long.parseLong(resultMerge.get("mergeTime")),
            new StatCounter.GraphStats(Integer.parseInt(resultMerge.get("nodes")),
                Integer.parseInt(resultMerge.get("edges"))));

        statCounter.addUCH(inputDataRow.root, Long.parseLong(resultMerge.get("uchTime")));

        return inputDataRow;
    }

    private static void addCGPoolToStatCounter(@NotNull final List<Map<String, String>> cgPool,
                                               @NotNull final StatCounter statCounter) {
        for (final var cg : cgPool) {
            statCounter.addNewCGtoPool(MavenCoordinate.fromString(cg.get("coordinate"), "jar"),
                Long.parseLong(cg.get("isolatedRevisionTime")),
                new StatCounter.GraphStats(Integer.parseInt(cg.get("nodes")),
                    Integer.parseInt(cg.get("edges"))));
        }
    }
}