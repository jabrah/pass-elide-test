/*
 * Copyright 2019, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */

package org.eclipse.pass.elide;

import com.google.common.collect.Lists;
import com.yahoo.elide.ElideSettings;
import com.yahoo.elide.ElideSettingsBuilder;
import com.yahoo.elide.core.datastore.DataStore;
import com.yahoo.elide.core.dictionary.EntityDictionary;
import com.yahoo.elide.core.filter.dialect.RSQLFilterDialect;
import com.yahoo.elide.datastores.aggregation.queryengines.sql.dialects.SQLDialectFactory;
import com.yahoo.elide.jsonapi.JsonApiMapper;
import com.yahoo.elide.jsonapi.links.DefaultJSONApiLinks;
import com.yahoo.elide.standalone.config.ElideStandaloneAnalyticSettings;
import com.yahoo.elide.standalone.config.ElideStandaloneAsyncSettings;
import com.yahoo.elide.standalone.config.ElideStandaloneSettings;
import com.yahoo.elide.standalone.config.ElideStandaloneSubscriptionSettings;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.servicelocator.ServiceLocator;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.pass.elide.filter.CorsFilter;

import java.io.IOException;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.TimeZone;

import javax.jms.ConnectionFactory;

/**
 * This class contains common settings for both test and production.
 */
public abstract class Settings implements ElideStandaloneSettings {

    protected String jdbcUrl;
    protected String jdbcUser;
    protected String jdbcPassword;

    protected boolean inMemory;

    public Settings(boolean inMemory) {
        jdbcUrl = Optional.ofNullable(System.getenv("JDBC_DATABASE_URL"))
                .orElse("jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1");

        jdbcUser = Optional.ofNullable(System.getenv("JDBC_DATABASE_USERNAME"))
                .orElse("sa");

        jdbcPassword = Optional.ofNullable(System.getenv("JDBC_DATABASE_PASSWORD"))
                .orElse("");

        this.inMemory = inMemory;
    }

    /**
     * {@inheritDoc}
     * 
     * Mostly copied from superclass, with the addition of the JSON:API links handling
     */
    @Override
    public ElideSettings getElideSettings(EntityDictionary dictionary, DataStore dataStore, JsonApiMapper mapper) {
        String jsonApiBaseUrl = getBaseUrl() + getJsonApiPathSpec().replaceAll("/\\*", "") + "/";
        
        ElideSettingsBuilder builder = new ElideSettingsBuilder(dataStore)
                .withEntityDictionary(dictionary)
                .withErrorMapper(getErrorMapper())
                .withJoinFilterDialect(RSQLFilterDialect.builder().dictionary(dictionary).build())
                .withSubqueryFilterDialect(RSQLFilterDialect.builder().dictionary(dictionary).build())
                .withBaseUrl(getBaseUrl())
                .withJsonApiPath(getJsonApiPathSpec().replaceAll("/\\*", ""))
                .withGraphQLApiPath(getGraphQLApiPathSpec().replaceAll("/\\*", ""))
                .withJsonApiMapper(mapper)
                .withAuditLogger(getAuditLogger())
                .withJSONApiLinks(new DefaultJSONApiLinks(jsonApiBaseUrl));

        if (verboseErrors()) {
            builder.withVerboseErrors();
        }

        if (getAsyncProperties().enableExport()) {
            builder.withExportApiPath(getAsyncProperties().getExportApiPathSpec().replaceAll("/\\*", ""));
        }

        if (enableISO8601Dates()) {
            builder.withISO8601Dates("yyyy-MM-dd'T'HH:mm'Z'", TimeZone.getTimeZone("UTC"));
        }

        return builder.build();
    }

    @Override
    public int getPort() {
        //Heroku exports port to come from $PORT
        return Optional.ofNullable(System.getenv("PORT"))
                .map(Integer::valueOf)
                .orElse(8080);
    }

    @Override
    public boolean enableSwagger() {
        return true;
    }

    @Override
    public String getSwaggerPathSepc() {
        return "/swagger/*";
    }

    @Override
    public boolean enableGraphQL() {
        return true;
    }

    @Override
    public String getModelPackageName() {
        return "org.eclipse.pass.elide.model";
    }

    @Override
    public ElideStandaloneAnalyticSettings getAnalyticProperties() {
        ElideStandaloneAnalyticSettings analyticPropeties = new ElideStandaloneAnalyticSettings() {
            @Override
            public boolean enableDynamicModelConfig() {
                return false;
            }

            @Override
            public boolean enableAggregationDataStore() {
                return false;
            }

            @Override
            public boolean enableMetaDataStore() {
                return false;
            }

            @Override
            public String getDefaultDialect() {
                if (inMemory) {
                    return SQLDialectFactory.getDefaultDialect().getDialectType();
                } else {
                    return SQLDialectFactory.getPostgresDialect().getDialectType();
                }
            }

            @Override
            public String getDynamicConfigPath() {
                return "src/main/resources/analytics";
            }
        };
        return analyticPropeties;
    }

    @Override
    public ElideStandaloneSubscriptionSettings getSubscriptionProperties() {
        return new ElideStandaloneSubscriptionSettings() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public String getPath() {
                return "/subscription";
            }

            @Override
            public ConnectionFactory getConnectionFactory() {
                return new ActiveMQConnectionFactory("vm://0");
            }
        };
    }

    @Override
    public ElideStandaloneAsyncSettings getAsyncProperties() {
        return new ElideStandaloneAsyncSettings() {

            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            public boolean enableCleanup() {
                return true;
            }

            @Override
            public boolean enableExport() {
                return false;
            }
        };
    }

    @Override
    public Properties getDatabaseProperties() {
        Properties dbProps;

        if (inMemory) {
            return getInMemoryProps();
        }

        try {
            dbProps = new Properties();
            dbProps.load(
                    Main.class.getClassLoader().getResourceAsStream("dbconfig.properties")
            );

            dbProps.setProperty("javax.persistence.jdbc.url", jdbcUrl);
            dbProps.setProperty("javax.persistence.jdbc.user", jdbcUser);
            dbProps.setProperty("javax.persistence.jdbc.password", jdbcPassword);
            return dbProps;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<Class<?>> getFilters() {
        return Lists.newArrayList(CorsFilter.class);
    }

    @Override
    public void updateServletContextHandler(ServletContextHandler servletContextHandler) {
       ResourceHandler resource_handler = new ResourceHandler();

       try {
           resource_handler.setDirectoriesListed(false);
           resource_handler.setResourceBase(Settings.class.getClassLoader()
                   .getResource("META-INF/resources/").toURI().toString());
           servletContextHandler.insertHandler(resource_handler);
       } catch (Exception e) {
           throw new IllegalStateException(e);
       }
    }

    // used instead of dbconf.properties
    protected Properties getInMemoryProps() {
        Properties options = new Properties();

        options.put("hibernate.show_sql", "true");
        options.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        options.put("hibernate.current_session_context_class", "thread");
        options.put("hibernate.jdbc.use_scrollable_resultset", "true");
        options.put("hibernate.default_batch_fetch_size", 100);

        options.put("javax.persistence.jdbc.driver", "org.h2.Driver");
        options.put("javax.persistence.jdbc.url", jdbcUrl);
        options.put("javax.persistence.jdbc.user", jdbcUser);
        options.put("javax.persistence.jdbc.password", jdbcPassword);

        // Create db and write out sql
        options.put("javax.persistence.schema-generation.database.action", "create");
        options.put("javax.persistence.schema-generation.scripts.action", "create");
        options.put("javax.persistence.schema-generation.create-database-schemas", "true");
        options.put("javax.persistence.schema-generation.scripts.create-target", "passdb.sql");

        return options;
    }

    public void runLiquibaseMigrations() throws Exception {
        //Run Liquibase Initialization Script
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
                new JdbcConnection(DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)));

        try (Liquibase liquibase = new liquibase.Liquibase(
                "db/changelog/changelog.xml",
                new ClassLoaderResourceAccessor(),
                database)) {
            liquibase.update("db1");
        }
    }
}
