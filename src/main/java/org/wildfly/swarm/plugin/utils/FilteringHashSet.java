package org.wildfly.swarm.plugin.utils;

import java.util.HashSet;
import java.util.function.Predicate;

public class FilteringHashSet<T> extends HashSet<T> {
    private final Predicate<T> filter;

    public FilteringHashSet(Predicate<T> filter) {
        super();
        this.filter = filter;
    }

    @Override
    public boolean add(T t) {
        if (filter.test(t)) {
            return super.add(t);
        }
        return false;
    }
}
