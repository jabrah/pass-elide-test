#! /bin/sh

DB_OPTS="-Djavax.persistence.schema-generation.database.action=create -Djavax.persistence.schema-generation.scripts.action=create -Djavax.persistence.schema-generation.create-database-schemas=true -Djavax.persistence.schema-generation.scripts.create-target=passdb.sql"

$JAVA_HOME/bin/java $DB_OPTS -jar pass-core-main/target/pass-core-main.jar
