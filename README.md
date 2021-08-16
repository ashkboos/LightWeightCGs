# LightWeightCGs

Using the following command it is possible to reproduce the experiments of the paper.
Note that the last step of the experiment requires 100g of memory.

1. `bash Runner_step1.sh final.rnd100.resolved.csv`
1. `bash Runner_step2.sh ` 

`final.rnd100.resolved.csv` is 100 randomly selected dependency sets that we used on the paper.
Using this dataset one can generate CGs for OPAL and Stitching, generate result CSV files, and then compare them by the
 scripts
 that we provide.

The scrip of such comparison is available at `overAll/stats.ipynb`.

* The configuration that we used for OPAl is available as `reference.conf`. Note that it is also included in the jar
 file.
 
 
 


