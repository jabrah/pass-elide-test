package org.dataconservancy.pass.doi.service;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

import com.yahoo.elide.Elide;
import com.yahoo.elide.RefreshableElide;
import com.yahoo.elide.core.RequestScope;
import com.yahoo.elide.core.datastore.DataStoreIterable;
import com.yahoo.elide.core.datastore.DataStoreTransaction;
import com.yahoo.elide.core.request.EntityProjection;
import com.yahoo.elide.spring.config.ElideConfigProperties;

/**
 * Attempt to use the internal Elide DataStoreTransaction.
 * There are complicated interplays between the Elide, RequestScope, and DataStoreTransaction
 * This below probably bypasses some of the hooks and checks.
 * Unclear that this is correct.
 */
public class ElidePassClient2 implements PassClient {
    private final Elide elide;

    public ElidePassClient2(RefreshableElide refreshableElide, ElideConfigProperties elideConfig) {
        this.elide = refreshableElide.getElide();
    }

    private RequestScope get_scope(DataStoreTransaction tx) {
        return new RequestScope(elide.getElideSettings().getBaseUrl(), null, null, null, tx, null, null, null, UUID.randomUUID(), elide.getElideSettings());
    }

    @Override
    public <T> void createObject(T obj) throws IOException {
        try (DataStoreTransaction tx = elide.getDataStore().beginTransaction()) {
            RequestScope scope = get_scope(tx);

            tx.preCommit(scope);
            tx.createObject(obj, scope);
            tx.flush(scope);
            tx.commit(scope);

            // TODO Does not work
            //if (scope.getNewPersistentResources().size() != 1) {
            //    throw new IOException("Expected one new resource, but was: " + scope.getNewPersistentResources().size());
            //}
            //return (T)scope.getNewPersistentResources().iterator().next().getObject();

            // Hack to get id = PassEntity.class.cast(obj).getId();
            // Object gets modified. Probably no need to return
        }
    }

    @Override
    public <T> void updateObject(T obj) throws IOException {
        try (DataStoreTransaction tx = elide.getDataStore().beginTransaction()) {
            RequestScope scope = get_scope(tx);

            tx.preCommit(scope);
            tx.save(obj, scope);
            tx.commit(scope);
            tx.flush(scope);
        }
    }

    @Override
    public <T> T getObject(Class<?> type, Long id) throws IOException {
        try (DataStoreTransaction tx = elide.getDataStore().beginTransaction()) {
            RequestScope scope = get_scope(tx);
            EntityProjection projection = EntityProjection.builder().type(type).build();

            return tx.loadObject(projection, id, scope);
        }
    }

    public <T> void visitObjects(Class<T> type, Consumer<T> action) throws IOException {
        try (DataStoreTransaction tx = elide.getDataStore().beginReadTransaction()) {
            RequestScope scope = get_scope(tx);
            EntityProjection projection = EntityProjection.builder().type(type).build();

            DataStoreIterable<T> iterable = tx.loadObjects(projection, scope);
            iterable.forEach(action);
        }
    }
}