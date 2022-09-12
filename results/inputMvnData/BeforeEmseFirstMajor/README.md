## highly.connected100.csv
The list of Maven coordinates of 100 highly connected dependents on Maven. 
Resulted by running [this sql query](https://github.com/fasten-project/fasten/blob/evaluation/analyzer/javacg-opal/src/main/java/eu/fasten/analyzer/javacgopal/evaluation/results/highly.connected.selector.sql) on FASTEN database.
          
## highly.connected100.resolved.csv
Same as `highly.connected100.csv`, but has one more column called `dependencies`.
`dependencies` column is list of dependency coordinates of each row.
By running [Shrinkwrap resolver](https://github.com/shrinkwrap/resolver) we collected dependencies of each coordinate.
There are overall 5538 package versions in this dataset, including 1783 unique package versions and
 3755 redundants.
 
## rnd100.csv
Randomly selected 100 Maven coordinates by `rnd` function of pandas from libraries.io dataset.

## rnd100.resolved.csv
`rnd100.csv` resolved by Shrinkwrap. This dataset has overall 2192 package versions, including 1844 unique package
 versions and 348 redundants.
 
## highly.connected3.resolved.csv
This dataset is a small selected version of highly connected one for experimentas and has 141 package versions in
 which 48 of them are redundant and 93 are unique.

