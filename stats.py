#!/usr/bin/env python
# coding: utf-8


import os
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from scipy import stats
import sys
from os import path

root = sys.argv[1]+"/"
directory = root+'figures'
if not path.exists(directory):
    os.mkdir(directory)
accuracy = pd.read_csv(root+"accuracy.csv")
overall = pd.read_csv(root+"Overall.csv")
input_data = pd.read_csv(root+"inputStats.csv")
files = pd.read_csv(root+"files.csv")
cg_pool = pd.read_csv(root+"CGPool.csv")

def mean_median_std(data, field):
    print("mean of %s: %f" %( field, data.mean()))
    print("std of %s: %f" %( field, data.std()))
    print("median of %s: %f" %( field, data.median()))
    print('\n')
    
def violin(data, field, path):
    fig, (ax1) = plt.subplots(nrows=1, ncols=1)
    ax1.violinplot(data[field], showmedians=True)
    ax1.set_title(field)
    plt.savefig(path)
    plt.close()
    
def cumulative_curve(data, field):
    plt.title(field)
    X2 = np.sort(data[field])/float(1000)
    F2 = np.array(range(len(data[field])))
    plt.plot(X2, F2)
    plt.ticklabel_format(useOffset=False)
    plt.close()
    
def remove_outliers(df):
    z_scores = stats.zscore(df)
    abs_z_scores = np.abs(z_scores)
    filtered_entries = (abs_z_scores < 3)
    new_df = df[filtered_entries]
    cond = df.isin(new_df)
    df2 = df.drop(df[cond].index)
    return new_df, df2

print("####### Analysis rate ####### \n ")
print("All : %s" %len(overall))
print("OPAL failed: %s"%len(overall[(overall['opalTime'] == 0)]))
print("Merge failed: %s"%len(overall[(overall['mergeTime'] == 0)]))
print("OPAL None: %s"%len(overall[overall['opalTime'].isna()]))
print("Merge None: %s"%len(overall[overall['mergeTime'].isna()]))
print("OPAL and Merge failed: %s"%len(overall[(overall['opalTime'] == 0) & (overall['mergeTime'] == 0)]))

print("####### Success Rate/File existence ####### \n ")
print("opalOutput : %s"%len(files[files["opalOutput"]] == 1))
print("opalCG : %s"%len(files[files["opalCG"]] == 1))
print("mergeOutput : %s"%len(files[files["mergeOutput"]] == 1))
print("CGPool : %s"%len(files[files["CGPool"]] == 1))
print("mergeCG : %s"%len(files[files["mergeCG"]] == 1))

print("####### partial success ####### \n ")
print("partial time not zero: %s"%len(cg_pool[cg_pool["isolatedRevisionTime"]] == 0))
print("all partials : %s"%len(cg_pool))

print("####### Accuracy Comparison ####### \n ")
overall = overall[~overall['opalTime'].isna()]
mean_median_std(accuracy['precision'], 'precision')
mean_median_std(accuracy['recall'], 'recall')
mean_median_std(accuracy['OPAL'], 'OPAL')
mean_median_std(accuracy['Merge'], 'Merge')
mean_median_std(accuracy['intersection'], 'intersection')
df = pd.DataFrame(dict(mean=[accuracy['precision'].mean(), accuracy['recall'].mean()],
                  std=[accuracy['precision'].std(), accuracy['recall'].std()],
                  median=[accuracy['precision'].median(), accuracy['recall'].median()]))
print(df.to_latex(index = True, index_names= True))
overall_fair = overall[(overall['opalTime'] != 0) & (overall['mergeTime'] != 0)]

print("####### Accuracy Plot ####### \n ")
fig, (ax1) = plt.subplots(nrows=1, ncols=2, sharey=True)
ax1[0].violinplot(accuracy['precision'], showmedians=True)
ax1[1].violinplot(accuracy['recall'], showmedians=True)
ax1[0].set_title('precision')
ax1[1].set_title('recall')
plt.savefig(directory+'/precisonRecall.pdf')
plt.close()


print("####### Edge Comparison ####### \n ")
mean_median_std(overall_fair['mergeEdges'], 'mergeEdges')
mean_median_std(overall_fair['opalEdges'], 'opalEdges')
print("merge edges: %d" %(overall_fair['mergeEdges'].sum()))
print("opal edges: %d" %(overall_fair['opalEdges'].sum()))

print("####### Edge Plot ####### \n ")
fig, (ax1) = plt.subplots(nrows=1, ncols=2, sharey=True)
mergeEdge = overall_fair[(overall_fair['mergeEdges']) !=0 & (overall_fair['mergeEdges'] != -1)]['mergeEdges']
opalEdge = overall_fair[(overall_fair['opalEdges'] !=0) & (overall_fair['opalEdges'] != -1)]['opalEdges']
ax1[0].violinplot(np.log(mergeEdge), showmedians=True)
ax1[1].violinplot(np.log(opalEdge), showmedians=True)
ax1[0].set_title('Stitching edges')
ax1[1].set_title('Opal edges')
plt.savefig(directory+'/edgeComparison.pdf')
plt.close()


print("####### Time Comparison ####### \n ")
merge = overall_fair['mergeTime']+overall_fair['UCHTime']
df = pd.DataFrame(dict(mean=[merge.mean(), overall_fair['opalTime'].mean(), overall_fair['cgPool'].mean()],
                  std=[merge.std(), overall_fair['opalTime'].std(), overall_fair['cgPool'].std()],
                  median=[merge.median(), overall_fair['opalTime'].median(), overall_fair['cgPool'].median()]))
print(df.to_latex(index = True, index_names= True))
mean_median_std(merge, "merge")
mean_median_std(overall_fair['opalTime'], 'opal')
mean_median_std(overall_fair['cgPool'], 'cgPool')

print("####### Time Plot ####### \n ")
fig, (ax1) = plt.subplots(nrows=1, ncols=3, sharey=True)
ax1[0].violinplot(np.log(overall_fair['opalTime']), showmedians=True)
ax1[1].violinplot(np.log(overall_fair['cgPool']), showmedians=True)
ax1[2].violinplot(np.log(merge), showmedians=True)
ax1[0].set_title('opal')
ax1[1].set_title('CG Cache')
ax1[2].set_title('Stitching')
plt.savefig(directory+'/timeViolin.pdf')
plt.close()


print("####### Input Data ####### \n ")
fig, (ax1) = plt.subplots(nrows=1, ncols=3, sharey=False)
# new, outliers = remove_outliers(input_data['depNum'])
exclude_parents = input_data[input_data['depNum'] != 1]
ax1[0].boxplot(np.log(exclude_parents['depNum']))
# print("depNum %s" % len(outliers))
# print(outliers)
ax1[0].set_title('Dependencies')
# new, outliers = remove_outliers(np.log(input_data['numFiles']))
ax1[1].boxplot(np.log(exclude_parents['numFiles']))
# print("numFiles %s" % len(outliers))
# print(outliers)
ax1[1].set_title('Files')
# new, outliers = remove_outliers(input_data['numFilesWithDeps'])
ax1[2].boxplot(np.log(exclude_parents['numFilesWithDeps']))
# print("numFileWithDeps %s" % len(outliers))
# print(outliers)
ax1[2].set_title('Files with deps')
fig.tight_layout(pad=1)
plt.savefig(directory+'/input.pdf')
plt.close()
print("exclude_parents size: %s" % len(exclude_parents))
print("depNum: %s"%exclude_parents['depNum'].mean())
print("numFiles: %s"%exclude_parents['numFiles'].mean())
print("numFilesWithDeps: %s"%exclude_parents['numFilesWithDeps'].mean())






