I'll extract the content from the HTML fragment and convert it to Markdown format:

# How to approach the project

1. Exploratory data analysis
2. Filtering the data
3. Choosing the correct hypothesis test
4. Performing the hypothesis test
5. Interpreting the result of the hypothesis test

## Steps to complete

### 1. Exploratory data analysis

Load the data from `men_results.csv` and `women_results.csv` to understand its contents.

#### Determining the column names, data types, and values
- The `.info()` method can be used to return a summary of the column names and data types.
- The `.value_counts()` method can be called on a categorical column, such as `tournament`, to determine the counts of the unique values in the column.

### 2. Filtering the data

Filter the data to only include official `FIFA World Cup` matches that took place after `2002-01-01`.

#### Filtering for FIFA World Cup matches
- The `tournament` column contains categorical values of the tournament name each match took place in.
- To filter rows containing a particular categorical column value, you can either use square-bracket subsetting with the `==` comparison operator, or you can call the `.isin()` method on the DataFrame column, specifying a list of values to filter for.

#### Filtering for matches after 2002-01-01
- Convert the `date` column to the `datetime` data type by passing it to `pd.to_datetime()`, or by using the `parse_dates` argument of `pd.read_csv()` when loading the data.
- Perform square-bracket subsetting with the `>` comparison operator to filter for rows after `2002-01-01`.

### 3. Choosing the correct hypothesis test

Use EDA to determine the appropriate hypothesis test for this dataset and scenario.

#### Determining the type of hypothesis test
- Because there are two independent groups, men's and women's, this scenario requires an *unpaired* two-sample test.
- An unpaired t-test and a Wilcoxon-Mann-Whitney test are the two most common two-sample tests, where the Wilcoxon-Mann-Whitney test is a non-parametric version of the unpaired t-test.
- To determine if a parametric or non-parametric test is appropriate, you'll need to verify the underlying assumptions of parametric tests, including checking the sample size in each group and the normality of each distribution.

#### Determining if the data is normally distributed
- The normal distribution is a symmetric bell-shaped curve, with the distribution centered around the mean.
- Plotting a histogram displaying the distribution of the number of goals scored in men's and women's matches will give you an idea about whether the dataset is normally distributed or not.
- If the normality is unclear from the plot, you can run a test of normality, such as a Kolmogorov–Smirnov test or the Shapiro–Wilk test.

### 4. Performing the hypothesis test

Once you've decided on the correct hypothesis test to perform, carry out the test to return the p-value.

#### How to perform the hypothesis test using pingouin
- `pingouin.mwu()` can be used to perform a Wilcoxon-Mann-Whitney test.
- `pingouin.mwu()` requires a DataFrame pivoted to wide-format.
- The `.pivot()` DataFrame method can be used to transform a DataFrame to wide format by specifying the `columns` and `values` arguments, the name of the column containing the values to turn into column headers and the column containing the values, respectively.
- `pingouin.mwu()` takes three arguments: `x`, the values from the pivoted table that you suspect are higher from the hypotheses, `y`, the values to compare against, and `alternative`, a string indicating whether the test is left-tailed, right-tailed, or two-tailed.

#### How to perform the hypothesis test using SciPy
- `scipy.stats.mannwhitneyu()` can be used to perform a Wilcoxon-Mann-Whitney test.
- The function takes three arguments: `x`, the values you suspect are higher from the hypotheses, `y`, the values to compare against, and `alternative`, a string indicating whether the test is left-tailed, right-tailed, or two-tailed.

#### Extracting the p-value using pingouin
- `pingouin.mwu()` returns a `pandas` DataFrame with a column named `p-val`; to extract the p-value, subset the column with square brackets, convert to a `numpy` array, and extract the float from the array.

#### Extracting the p-value using SciPy
- To extract the p-value from the object returned by `scipy.stats.mannwhitneyu()`, call the `.pvalue` attribute.

### 5. Interpreting the result of the hypothesis test

Interpret the p-value to determine if there is statistical significance between the two groups, assuming a 10% significance level.

#### Determining the result from the p-value and significance level
- If the p-value is less than or equal to the significance level of 10% (`0.01`), reject the null hypothesis; otherwise, fail to reject it.