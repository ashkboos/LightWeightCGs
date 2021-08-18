#!/usr/bin/env python
# coding: utf-8

# In[1]:


import os
import pandas as pd
from scipy.stats import norm  
import matplotlib.pyplot as plt
import numpy as np
from scipy import stats
import sys
from os import path
# In[3]:

root = sys.argv[1]+"/"
directory = root+'figures'
if not path.exists(directory):
    os.mkdir(directory)
accuracy = pd.read_csv(root+"accuracy.csv")
overall = pd.read_csv(root+"Overall.csv")
overall = overall[~overall['opalTime'].isna()]
input_data = pd.read_csv(root+"inputStats.csv")


# In[3]:


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
    # plt.show()
    plt.close()
    
def cumulative_curve(data, field):
    plt.title(field)
    X2 = np.sort(data[field])/float(1000)
    F2 = np.array(range(len(data[field])))
    plt.plot(X2, F2)
    plt.ticklabel_format(useOffset=False)
    # plt.show()
    plt.close()
    
def remove_outliers(df):
    z_scores = stats.zscore(df)
    abs_z_scores = np.abs(z_scores)
    filtered_entries = (abs_z_scores < 3)
    new_df = df[filtered_entries]
    cond = df.isin(new_df)
    df2 = df.drop(df[cond].index)
    return new_df, df2


# In[8]:


print("####### Accuracy Comparison ####### \n ")
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

print("####### Edge Comparison ####### \n ")
mean_median_std(overall_fair['mergeEdges'], 'mergeEdges')
mean_median_std(overall_fair['opalEdges'], 'opalEdges')

print("merge edges: %d" %(overall_fair['mergeEdges'].sum()))
print("opal edges: %d" %(overall_fair['mergeEdges'].sum()))

print("####### Time Comparison ####### \n ")
df = pd.DataFrame(dict(mean=[overall_fair['mergeTime'].mean(), overall_fair['opalTime'].mean(), overall_fair['cgPool'].mean()],
                  std=[overall_fair['mergeTime'].std(), overall_fair['opalTime'].std(), overall_fair['cgPool'].std()],
                  median=[overall_fair['mergeTime'].median(), overall_fair['opalTime'].median(), overall_fair['cgPool'].median()]))

print(df.to_latex(index = True, index_names= True))
merge = overall_fair['mergeTime']+overall_fair['UCHTime']
mean_median_std(merge, "merge")
# mean_median_std(overall_fair, 'UCHTime')
mean_median_std(overall_fair['opalTime'], 'opal')


# In[16]:

print("####### Accuracy Comparison ####### \n ")
fig, (ax1) = plt.subplots(nrows=1, ncols=2, sharey=True)
ax1[0].violinplot(accuracy['precision'], showmedians=True)
ax1[1].violinplot(accuracy['recall'], showmedians=True)
ax1[0].set_title('precision')
ax1[1].set_title('recall')
plt.savefig(directory+'/precisonRecall.pdf')
# plt.show()
plt.close()


print("####### Edge Comparison ####### \n ")
fig, (ax1) = plt.subplots(nrows=1, ncols=2, sharey=True)
ax1[0].violinplot(overall_fair['mergeEdges'], showmedians=True)
ax1[1].violinplot(overall_fair['opalEdges'], showmedians=True)
ax1[0].set_title('Stitching edges')
ax1[1].set_title('Opal edges')
plt.savefig(directory+'/edgeComparison.pdf')
# plt.show()
plt.close()

print("####### Time Comparison ####### \n ")
fig, (ax1) = plt.subplots(nrows=1, ncols=3, sharey=True)
ax1[0].violinplot(overall_fair['opalTime'], showmedians=True)
ax1[1].violinplot(overall_fair['cgPool'], showmedians=True)
ax1[2].violinplot(merge, showmedians=True)
ax1[0].set_title('opal')
ax1[1].set_title('CG Cache')
ax1[2].set_title('Stitching')
plt.savefig(directory+'/timeViolin.pdf')
# plt.show()
plt.close()


# In[17]:


print("####### Input Data ####### \n ")
fig, (ax1) = plt.subplots(nrows=1, ncols=3, sharey=False)

new, outliers = remove_outliers(input_data['depNum'])
ax1[0].boxplot(new)
print("depNum %s" % len(outliers))
print(outliers)
ax1[0].set_title('Dependencies')

new, outliers = remove_outliers(input_data['numFiles'])
ax1[1].boxplot(new)
print("numFiles %s" % len(outliers))
print(outliers)
ax1[1].set_title('Files')

new, outliers = remove_outliers(input_data['numFilesWithDeps'])
ax1[2].boxplot(new)
print("numFileWithDeps %s" % len(outliers))
print(outliers)
ax1[2].set_title('Files with deps')

fig.tight_layout(pad=1)
plt.savefig(directory+'/input.pdf')
# plt.show()
plt.close()


# In[18]:


print("depNum: %s"%input_data['depNum'].mean())
print("numFiles: %s"%input_data['numFiles'].mean())
print("numFilesWithDeps: %s"%input_data['numFilesWithDeps'].mean())


# In[19]:


print("####### Accuracy Comparison ####### \n ")
cumulative_curve(accuracy, 'precision')
cumulative_curve(accuracy, 'recall')
cumulative_curve(accuracy, 'OPAL')
cumulative_curve(accuracy, 'Merge')
cumulative_curve(accuracy, 'intersection')


# In[ ]:




