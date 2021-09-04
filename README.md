# LightWeightCGs

Using the following command it is possible to reproduce the experiments of the paper.
Note that the second step of the experiment requires more than 100g of memory.

1. `bash Runner_step1.sh rnd2000.rndpckg.rndver.resolved.csv callGraphFolder RTA`
1. `bash Runner_step2.sh callGraphFolder resultFolder rnd2000.rndpckg.rndver.resolved.csv ` 

`rnd2000.rndpckg.rndver.resolved.csv` is 2000 randomly selected dependency sets that we used on the paper.
Using this dataset one can generate CGs for OPAL and Frankenstein, generate result CSV files, and then compare them
by the scripts that we provide.

The scrip of such comparison is available at `script.py`.

* The configuration that we used for OPAl is available as `reference.conf`. Note that it is also included in the jar
 file.
 
 
 


