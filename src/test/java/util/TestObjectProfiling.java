package util;

import static eu.fasten.analyzer.javacgwala.data.callgraph.Algorithm.CHA;
import static util.FilesUtils.JAVA_8_COORD;

import eu.fasten.core.data.CallPreservationStrategy;
import eu.fasten.core.data.Constants;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.GraphLayout;
import org.openjdk.jol.vm.VM;


public class TestObjectProfiling {

    @Test
    public void test() {

        System.out.println(VM.current().details());
        final var pcg = CGUtils.generatePCG(new File[] {FilesUtils.getRTJar()},
            JAVA_8_COORD, CHA.label, CallPreservationStrategy.ONLY_STATIC_CALLSITES,
            Constants.opalGenerator);
        for (int i = 0; i < 5; i++) {
            System.out.println(getInstanceSize(pcg));
        }
        System.out.println("optimizing for merge: ");
        pcg.setSourceCallSitesToOptimizeMerge();
        for (int i = 0; i < 5; i++) {
            System.out.println(getInstanceSize(pcg));
        }
    }


    private double getInstanceSize(Object obj) {
        final GraphLayout graphLayout = GraphLayout.parseInstance(obj);
        return (double) graphLayout.totalSize() / (1024 * 1024);
    }

}

