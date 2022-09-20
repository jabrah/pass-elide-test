package org.dataconservancy.pass.doi.service;

import java.io.IOException;
import java.util.function.Consumer;

// Need to investigate how objects with relationships get loaded
interface PassClient {
    <T> void createObject(T obj) throws IOException;

    <T> void updateObject(T obj) throws IOException;

    <T> T getObject(Class<?> type, Long id) throws IOException;

    <T> void visitObjects(Class<T> type, Consumer<T> action) throws IOException;
}
