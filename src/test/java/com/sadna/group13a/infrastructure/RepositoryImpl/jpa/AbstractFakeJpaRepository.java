package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.FluentQuery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * In-memory stand-in for a Spring Data JpaRepository, for tests that wire
 * RepositoryImpl classes by hand without a Spring context. Implements only
 * the CRUD operations actually used in this codebase; everything else
 * throws UnsupportedOperationException.
 */
public abstract class AbstractFakeJpaRepository<T, ID> implements JpaRepository<T, ID> {

    private final Map<ID, T> store = new LinkedHashMap<>();
    private final Function<T, ID> idExtractor;

    protected AbstractFakeJpaRepository(Function<T, ID> idExtractor) {
        this.idExtractor = idExtractor;
    }

    @Override
    public <S extends T> S save(S entity) {
        store.put(idExtractor.apply(entity), entity);
        return entity;
    }

    @Override
    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        List<S> saved = new ArrayList<>();
        entities.forEach(e -> saved.add(save(e)));
        return saved;
    }

    @Override
    public Optional<T> findById(ID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public boolean existsById(ID id) {
        return store.containsKey(id);
    }

    @Override
    public List<T> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<T> findAllById(Iterable<ID> ids) {
        List<T> result = new ArrayList<>();
        ids.forEach(id -> {
            T v = store.get(id);
            if (v != null) result.add(v);
        });
        return result;
    }

    @Override
    public long count() {
        return store.size();
    }

    @Override
    public void deleteById(ID id) {
        store.remove(id);
    }

    @Override
    public void delete(T entity) {
        store.remove(idExtractor.apply(entity));
    }

    @Override
    public void deleteAllById(Iterable<? extends ID> ids) {
        ids.forEach(store::remove);
    }

    @Override
    public void deleteAll(Iterable<? extends T> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public void deleteAll() {
        store.clear();
    }

    @Override
    public List<T> findAll(Sort sort) {
        throw new UnsupportedOperationException("not used by this codebase");
    }

    @Override
    public Page<T> findAll(Pageable pageable) {
        throw new UnsupportedOperationException("not used by this codebase");
    }

    @Override
    public void flush() {
        throw new UnsupportedOperationException("not used by this codebase");
    }

    @Override
    public <S extends T> S saveAndFlush(S entity) {
        throw new UnsupportedOperationException("not used by this codebase");
    }

    @Override
    public <S extends T> List<S> saveAllAndFlush(Iterable<S> entities) {
        throw new UnsupportedOperationException("not used by this codebase");
    }

    @Override
    public void deleteAllInBatch(Iterable<T> entities) {
        throw new UnsupportedOperationException("not used by this codebase");
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<ID> ids) {
        throw new UnsupportedOperationException("not used by this codebase");
    }

    @Override
    public void deleteAllInBatch() {
        throw new UnsupportedOperationException("not used by this codebase");
    }

    @Override
    @SuppressWarnings("deprecation")
    public T getOne(ID id) {
        throw new UnsupportedOperationException("not used by this codebase");
    }

    @Override
    @SuppressWarnings("deprecation")
    public T getById(ID id) {
        throw new UnsupportedOperationException("not used by this codebase");
    }

    @Override
    public T getReferenceById(ID id) {
        throw new UnsupportedOperationException("not used by this codebase");
    }

    @Override
    public <S extends T> Optional<S> findOne(Example<S> example) {
        throw new UnsupportedOperationException("not used by this codebase");
    }

    @Override
    public <S extends T> List<S> findAll(Example<S> example) {
        throw new UnsupportedOperationException("not used by this codebase");
    }

    @Override
    public <S extends T> List<S> findAll(Example<S> example, Sort sort) {
        throw new UnsupportedOperationException("not used by this codebase");
    }

    @Override
    public <S extends T> Page<S> findAll(Example<S> example, Pageable pageable) {
        throw new UnsupportedOperationException("not used by this codebase");
    }

    @Override
    public <S extends T> long count(Example<S> example) {
        throw new UnsupportedOperationException("not used by this codebase");
    }

    @Override
    public <S extends T> boolean exists(Example<S> example) {
        throw new UnsupportedOperationException("not used by this codebase");
    }

    @Override
    public <S extends T, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
        throw new UnsupportedOperationException("not used by this codebase");
    }
}
