# How to approach the project

## Steps to complete

### 1. Preparing the data

Load and prepare the grocery transaction data for analysis.

#### Loading grocery data
- Use `read_csv()` to load `grocery_data1.csv` and `grocery_data2.csv`.

#### Ensuring date parsing is handled appropriately
- Use `mdy()` and `dmy()` to parse dates.

### 2. Merging and transforming data

Combine grocery data from different sources and transform it to include key calculations.

#### Combining datasets
- Use `bind_rows()` to merge `grocery_data1` with `grocery_data2` after dates parsed correctly.

#### Calculating total sales
- Use `mutate()` to add a `TotalSaleUSD` column using `PriceUSD` and `Quantity`.

### 3. Exploring customer purchasing patterns

Perform grouping and creating of new variables to understand customer purchasing patterns over time.

#### Grouping and calculating time since last purchase
- Group by `CustomerID` and `ProductName`, then use `mutate()` with `c()` and `diff()` to calculate the difference between consecutive dates; the first element of `c()` should be `0` to account for the first purchase date.

#### Extract dates and times
- Use `lubridate` functions such as `week()`, `year()`, `hour()` to extract granular date and time data for further analysis.

### 4. Performing temporal analysis

Dive deeper into the time-based aspects of grocery sales, exploring weekly and hourly trends.

#### Analyzing weekly and hourly sales
- Create two data frames: one grouping data by `Week` and `Year` for weekly sales analysis and another by `Hour` for hourly sales insights.

### 5. Analyzing weekly sales

Determine the week with the smallest deviation from the average weekly sales.

#### Find the mean weekly sales number
- Calculate the mean of `WeeklyTotalSaleUSD`.

#### Identify week with smallest absolute deviation in sales
- Add a `Diff` column representing the absolute deviation from the mean for each week, assigning the appropriate week number for the smallest change from the mean as `smallest_sales_deviation`.

### 6. Examining hourly sales

Calculate which hour of the day experiences the highest total sales.

#### Find hour with highest sales
- Summarize hourly total sales for each hour and arrange in descending order to find the hour with the highest sales, assigning the appropriate hour number as `most_hourly_sales`.

### 7. Studying a product-specific purchase pattern

Examine the purchase pattern of Corn Flakes for a specific customer.

#### Explore the days since last purchase for `CustomerID` 107
- Filter for `CustomerID` 107 and `ProductName` `"Corn Flakes"`. Then, calculate the days elapsed between each consecutive purchase, assigning the days between purchases into the `corn_flakes_days` vector with two entries.