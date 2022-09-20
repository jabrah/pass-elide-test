package org.dataconservancy.pass.doi.service;

import java.io.IOException;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yahoo.elide.Elide;
import com.yahoo.elide.RefreshableElide;
import com.yahoo.elide.spring.config.ElideConfigProperties;
import com.yahoo.elide.utils.HeaderUtils;

// Try to directly use the Elide API
// This means doing JSON API serializations
// Could try to use the RestController directly?
// Just a sketch, not working
public class ElidePassClient1 implements PassClient {
    private final Elide elide;
    private final ElideConfigProperties elideConfig;

    public ElidePassClient1(RefreshableElide refreshableElide, ElideConfigProperties elideConfig) {
        this.elide = refreshableElide.getElide();
        this.elideConfig = elideConfig;
    }

    @Override
    public <T> void createObject(T obj) throws IOException {
        String api_version = HeaderUtils.resolveApiVersion(null);
        String json;

        try {
            json = elide.getMapper().writeJsonApiDocument(obj);
        } catch (JsonProcessingException e) {
            throw new IOException(e);
        }

        String base_url = elideConfig.getBaseUrl();

        // TODO Better way to get path?
        String json_type = obj.getClass().getName();
        json_type = Character.toLowerCase(json_type.charAt(0)) + json_type.substring(1);
        String path = "/" + json_type;

        elide.post(base_url, path, json, null, api_version);
    }

    @Override
    public <T> void updateObject(T obj) {

    }

    @Override
    public <T> T getObject(Class<?> type, Long id) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T> void visitObjects(Class<T> type, Consumer<T> action) throws IOException {
        // TODO Auto-generated method stub

    }
}
