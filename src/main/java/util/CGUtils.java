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

import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;
import eu.fasten.analyzer.javacgopal.data.CGAlgorithm;
import eu.fasten.analyzer.javacgopal.data.OPALCallGraph;
import eu.fasten.analyzer.javacgopal.data.OPALCallGraphConstructor;
import eu.fasten.analyzer.javacgopal.data.OPALPartialCallGraphConstructor;
import eu.fasten.analyzer.javacgwala.data.callgraph.Algorithm;
import eu.fasten.analyzer.javacgwala.data.callgraph.CallGraphConstructor;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraphGenerator;
import eu.fasten.analyzer.javacgwala.data.callgraph.analyzer.WalaResultAnalyzer;
import eu.fasten.core.data.CallPreservationStrategy;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.data.opal.MavenCoordinate;
import java.io.File;
import java.io.IOException;

public class CGUtils {


    public static PartialJavaCallGraph generateCGFromFile(final File file,
                                                          final CGAlgorithm algorithm,
                                                          final CallPreservationStrategy callPreservationStrategy,
                                                          final String coord,
                                                          final String generator) {

        return generateCGFromFile(file, MavenCoordinate.fromString(coord, ""), algorithm,
            callPreservationStrategy, generator);
    }

    public static PartialJavaCallGraph generateCGFromFile(final File file,
                                                          final MavenCoordinate mavenCoordinate,
                                                          final CGAlgorithm algorithm,
                                                          final CallPreservationStrategy callPreservationStrategy,
                                                          final String generator) {

        if (generator.equals(Constants.opalGenerator)) {
            final var opalCG = new OPALCallGraphConstructor().construct(file, algorithm);
            return convertOpalCGToFastenCG(mavenCoordinate, callPreservationStrategy, opalCG);
        } else {
            final CallGraph callgraph;
            try {
                callgraph = CallGraphConstructor.generateCallGraph(file.getAbsolutePath(),
                    Algorithm.CHA);
            } catch (IOException | ClassHierarchyException | CancelException e) {
                throw new RuntimeException(e);
            }

            final var pcg =
                PartialCallGraphGenerator.generateEmptyPCG(Constants.mvnForge,
                    mavenCoordinate.getProduct(), mavenCoordinate.getVersionConstraint(), -1,
                    Constants.walaGenerator);

            WalaResultAnalyzer.wrap(callgraph, pcg, callPreservationStrategy);
            return pcg;
        }
    }


    public static PartialJavaCallGraph convertOpalCGToFastenCG(
        final MavenCoordinate mavenCoordinate,
        final CallPreservationStrategy callPreservationStrategy,
        final OPALCallGraph opalCG) {
        final var partialCallGraph =
            new OPALPartialCallGraphConstructor().construct(opalCG, callPreservationStrategy);

        return new PartialJavaCallGraph(Constants.mvnForge, mavenCoordinate.getProduct(),
            mavenCoordinate.getVersionConstraint(), -1,
            Constants.opalGenerator,
            partialCallGraph.classHierarchy,
            partialCallGraph.graph);
    }

}
