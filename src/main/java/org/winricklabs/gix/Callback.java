package org.winricklabs.gix;

public interface Callback<T> {
    void call(T t);
}
