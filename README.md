# Introduction

This is a test of exposing the PASS data model using Elide.

It is based on https://github.com/yahoo/elide-standalone-example.

# Building

Java 11 and Maven 3.8 required.

```
mvn clean package
```

# Running

```
java -jar pass-elide-test.jar
```

On startup tables will be created and the schema used will be written to pass.db.sql.
By default an in memory database is used.

Look at http://localhost:8080/ to see the auto-created documentation and a UI for testing out the api. You can directly make request with the UI and see what happens. Note when doing a POST to create an object, be sure to edit the type field to have the correct object type and delete the id field to have the id auto-generated.

## Running with Postgres

First startup Postgres with:

```
docker-compose up
```

Then before running the jar as above:

```
export JDBC_DATABASE_URL=jdbc:postgresql://localhost:5432/pass
export JDBC_DATABASE_USERNAME=pass
export JDBC_DATABASE_PASSWORD=moo
```

# Known issues

  * Enums are stored in the db using their uppercase name, not the intended value. This can be fixed with custom converters.
  * The provided json api console gets the PATCH syntax wrong. There must be a data member of the relationship object in the JSON.

 ```
 {
  "data": {
    "type": "journal",
    "id": "1",
    "relationships": {
      "publisher": {
        "data": {
          "id": "2",
          "type": "publisher"
        }
      }
    }
  }
}
```
  
