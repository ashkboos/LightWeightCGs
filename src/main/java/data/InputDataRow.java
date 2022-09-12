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

import eu.fasten.core.data.opal.MavenCoordinate;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class InputDataRow {
    public MavenCoordinate root;
    public List<MavenCoordinate> deps;

    public InputDataRow(MavenCoordinate root,
                        List<MavenCoordinate> deps) {
        this.root = root;
        this.deps = deps;
    }

    @NotNull
    public static InputDataRow initEmptyInputDataRow() {
        return new InputDataRow(MavenCoordinate.fromString("", ""), new ArrayList<>());
    }

    public boolean isEmpty(){
        return deps.isEmpty();
    }

    public void addToDepSet(@NotNull final String dep){
        this.deps.add(MavenCoordinate.fromString(dep, "jar"));
    }

    public void addRoot(@NotNull final String root){
        this.root = MavenCoordinate.fromString(root, "jar");
    }
}
