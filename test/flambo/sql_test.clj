(ns flambo.sql-test
  (:use midje.sweet)
  (:require [flambo.api   :as f]
            [flambo.conf  :as conf]
            [flambo.sql   :as sql]))

(facts
 "about spark-sql-context"
 (let [conf (-> (conf/spark-conf)
                (conf/master "local[*]")
                (conf/app-name "sql-test"))]
   (sql/with-sql-context c conf
     (let [sc (sql/spark-context c)
           test-data ["{\"col1\":4,\"col2\":\"a\"}" "{\"col1\":6,\"col2\":\"a\"}" "{\"col1\":5,\"col2\":\"b\"}"]
           test-df (sql/json-rdd c (f/parallelize sc test-data))
           test-data-2 ["{\"col1\":4,\"col2\":\"a\"}" "{\"col1\":4,\"col2\":\"a\"}" "{\"col1\":6,\"col2\":\"a\"}"]
           test-df-2 (sql/json-rdd c (f/parallelize sc test-data-2))
           _ (sql/register-data-frame-as-table c test-df "foo")
           _ (sql/register-data-frame-as-table c test-df-2 "bar")]

       (fact
         "with-sql-context gives us a SQLContext"
         (class c) => org.apache.spark.sql.SQLContext)

       (fact
         "load gives us a DataFrame"
         (class (sql/load c "test/resources/data.csv" "com.databricks.spark.csv")) => org.apache.spark.sql.DataFrame)

       (fact
         "returns an array of column names from a CSV file"
         (let [df (sql/read-csv c "test/resources/cars.csv" :header true)]
           (sql/columns df) => ["year" "make" "model" "comment" "blank"]))

       (fact "SQL queries work"
         (f/count (sql/sql c "SELECT * FROM foo WHERE col2 = 'a'")) => 2)

       (fact "table-names gets all tables"
         (sql/table-names c) => (just ["foo" "bar"] :in-any-order))

       (fact "table returns dataframe with the data for given name"
         (f/first (sql/table c "foo")) => (f/first test-df)
         (f/count (sql/table c "foo")) => (f/count test-df))

       (fact "print schema displays a the dataframe schema"
         (sql/print-schema test-df))

       (fact "cache table puts a given table into the cache"
         (let [_ (sql/cache-table c "foo")]
           (sql/is-cached? c "foo")) => true)

       (fact "uncache table removes a table from the cache"
         (let [_ (sql/cache-table c "bar")
               _ (sql/uncache-table c "bar")]
           (sql/is-cached? c "bar")) => false)

       (fact "clear-cache removes all tables from the cache"
         (let [_ (sql/cache-table c "foo")
               _ (sql/cache-table c "bar")
               _ (sql/clear-cache c)]
           (or (sql/is-cached? c "foo") (sql/is-cached? c "bar"))) => false)

       ))))
