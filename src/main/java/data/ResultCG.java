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

package data;

import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.MergedDirectedGraph;
import it.unimi.dsi.fastutil.Pair;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class ResultCG {
    public DirectedGraph dg;
    public Map<Long, String> uris;

    public ResultCG(DirectedGraph dg, Map<Long, String> uris) {
        this.dg = dg;
        this.uris = uris;
    }

    public ResultCG() {
        this.dg = new MergedDirectedGraph();
        this.uris = new HashMap<>();
    }

    public ResultCG(@NotNull Pair<DirectedGraph, Map<Long, String>> cg) {
        this.dg = cg.first();
        this.uris = cg.second();
    }

    public boolean isEmpty() {
        return this.uris.isEmpty();
    }

}
