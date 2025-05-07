# How to approach the project

1. Preparing the data
2. Determining number of optimal clusters with elbow plot
3. Calculating distance and clustering hierarchically
4. Creating and visualizing dendrograms 
5. Assigning clusters and segmenting data
6. Analyzing clusters and specifying the features of strongest influence

## Steps to complete

### 1. Preparing the data

Load the NBA player data, calculate (points + rebounds + assists) per game, set player names as row names, and scale the data.

#### Reading NBA player data
- Use `read_csv()` to load `nba_players_2023.csv`.

#### Creating a new column
- Use `mutate()` to create a `pra_per_game` (points + rebounds + assists) variable.

#### Setting rownames
- To have easy labeling in the dendrograms, set player names as row names with `column_to_rownames()`.

#### Normalizing the data
- Apply the `scale()` function.

### 2. Determining number of optimal clusters with elbow plot

Create an elbow plot to determine the optimal number of clusters for K-means clustering.

#### Creating a total within-cluster sum of squares function
- Use `map_dbl()` and `kmeans()` to calculate within-cluster sum of squares (WSS) for a range of `centers` values.

#### Making a WSS data frame
- Store the number of cluster options and WSS values in a data frame to prepare for plotting.

#### Plotting the elbow plot
- Use `ggplot()` to visualize the WSS against different `k` values.

#### Identifying the optimal cluster number
- Look for the elbow in the plot to decide the optimal number of clusters, assigning that to `num_clusters`.

### 3. Calculating distance and clustering hierarchically

Calculate the Euclidean distance between players and perform hierarchical clustering to quantitatively measure the similarities and differences in player performance metrics.

#### Finding Euclidean distance
- Use `dist()` with appropriate method to compute distances between players.

#### Performing hierarchical clustering
- Apply `hclust()` on the distance matrix with the 'average' linkage method.

### 4. Creating and visualizing dendrograms

Create a dendrogram from the hierarchical clustering and visualize it in colored form.

#### Building a dendrogram and coloring its branches
- Convert the clustering result to a dendrogram with `as.dendrogram()` using `color_branches()` to color dendrogram branches according to `num_clusters`.

### 5. Assigning clusters and segmenting data

Assign cluster labels to players and segment the data frame based on these clusters.

#### Designing a cluster assignment vector
- Use `cutree()` to assign cluster labels.

#### Segmenting players based on clusters
- Combine player data with cluster labels to create a segmented data frame with `mutate()`.

### 6. Analyzing clusters and specifying the features of strongest influence

Explore and view the clusters, examining key statistics by each of the six numeric variables, and determining the variables that best differentiate the clusters.

#### Analyzing cluster distributions
- Determine summarize statistics (mean, standard deviation, median, minimum, and maximum) of each variable for each cluster.

#### Identifying the most influential variables
- Set `strongest_influence` to which ones of the six variables have no overlap between clusters.