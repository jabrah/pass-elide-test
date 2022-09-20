# Introduction

This is a test of exposing the PASS data model using Elide.

It is based on https://github.com/yahoo/elide-standalone-example.

# Building

Java 11 and Maven 3.8 required.

```
mvn clean package
```

# Running local build

```
java -jar pass-core-main.jar
```

On startup tables will be created and the schema used will be written to pass.db.sql.
By default an in memory database is used.

Look at http://localhost:8080/ to see the auto-created documentation and a UI for testing out the api. You can directly make request with the UI and see what happens. Note when doing a POST to create an object, be sure to edit the type field to have the correct object type and delete the id field to have the id auto-generated.

## Running with Docker setup

This uses Postgres.

Just run:
```
docker-compose up -d
```

# Using JSON API

All of our data model is available, just divided into attributes and relationshiops. Note that ids are now integers, not URIs.

## Creating a RepositoryCopy

```
curl -v -X POST "http://localhost:8080/object/repositoryCopy" -H "accept: application/vnd.api+json" -H "Content-Type: application/vnd.api+json" -d @rc1.json
```

*rc1.json:*
```
{
  "data": {
    "type": "repositoryCopy",
    "attributes": {
      "accessUrl": "why",
      "copyStatus": "ACCEPTED"
    }
  }
}
```

## Patch a Journal

```
curl -X PATCH "http://localhost:8080/object/journal/1" -H "accept: application/vnd.api+json" -H "Content-Type: application/vnd.api+json" -d @patch.json
```

*patch.json:*
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


# Known issues

  * Enums are stored in the db using their uppercase name, not the intended value. This can be fixed with custom converters.
  * Need nicer endpoint. Maybe /data. 
  * RepositoryCopy in Java becomes repositoryCopy to JSON API. Do we like this or not?
  * The provided json api console gets the PATCH syntax wrong. There must be a data member of the relationship object in the JSON.
  * Can tighten up some of the type handling. For example I changed all our URI to string, but some may make sense as URI. Can add Serdes for them.

