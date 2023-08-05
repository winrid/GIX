package com.winricklabs.gix;

import com.badlogic.gdx.utils.Array;

import java.lang.reflect.Constructor;

public class GIXMatchingConstructor {
    public Constructor<?> constructor;
    public Array<GIXAttr> matching_attributes;
    public boolean requires_actors;
    public boolean requires_parent_gix_node;

    GIXMatchingConstructor(Constructor<?> constructor) {
        this.constructor = constructor;
        this.matching_attributes = null;
    }

    GIXMatchingConstructor(Constructor<?> constructor, Array<GIXAttr> matching_attributes) {
        this.constructor = constructor;
        this.matching_attributes = matching_attributes;
    }

    GIXMatchingConstructor(Constructor<?> constructor, Array<GIXAttr> matching_attributes, boolean requires_actors) {
        this.constructor = constructor;
        this.matching_attributes = matching_attributes;
        this.requires_actors = requires_actors;
    }

    GIXMatchingConstructor(Constructor<?> constructor, Array<GIXAttr> matching_attributes, boolean requires_actors, boolean requires_parent_gix_node) {
        this.constructor = constructor;
        this.matching_attributes = matching_attributes;
        this.requires_actors = requires_actors;
        this.requires_parent_gix_node = requires_parent_gix_node;
    }

}
