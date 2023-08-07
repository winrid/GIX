package com.winricklabs.gix;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

import org.jsoup.nodes.Node;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;

public class GIXNode {
    final private Object ui_state;
    final public Node domNode;
    final public String name;
    final public GIXNodeTarget target;
    final public GIXNode parent;
    public Array<GIXNode> children;
    final public Array<GIXAttr> attrs;
    final public Array<GIXAttr> constructor_attrs;
    public Object ui_instance; // could be Actor or implements Actor (cell) - improve type safety?
    public ObjectMap<String, Object> context;

    GIXNode(Object ui_state, Node domNode, GIXNodeTarget target, GIXNode parent, Array<GIXAttr> attrs, Array<GIXAttr> constructor_attrs) {
        this.ui_state = ui_state;
        this.domNode = domNode;
        this.name = domNode.nodeName();
        this.target = target;
        this.parent = parent;
        this.attrs = attrs;
        this.constructor_attrs = constructor_attrs;
    }

    void addChild(GIXNode node) {
        if (children == null) {
            children = new Array<>(1);
        }
        children.add(node);
    }

    void addContext(String key, Object value) {
        if (this.context == null) {
            this.context = new ObjectMap<>();
        }
        this.context.put(key, value);
    }

    void setUIInstance(Object ui_instance, ObjectMap<String, Actor> actorObjectMap) {
        this.ui_instance = ui_instance;
        GIXAttr idAttr = getAttr("id");
        if (idAttr != null) {
            actorObjectMap.put(idAttr.getRawValue(), (Actor) ui_instance);
        }
    }

    void iterate(Callback<GIXNode> callback) {
        callback.call(this);
        if (this.children != null) {
            for (GIXNode node : this.children) {
                node.iterate(callback);
            }
        }
    }

    void expandLoops() {
        this.children = this.getExpandedChildren();
    }

    private Array<GIXNode> getExpandedChildren() {
        if (this.children == null) {
            return null;
        }
        if (this.name.equals("Repeat")) { // TODO HACK, so bad. I am so guilty. I am sorry. Please forgive.
            Array<GIXNode> new_children = new Array<>();
            Array<?> data = null;
            String as = null;
            for (GIXAttr attr : attrs) {
                if (attr.name.equals("with")) { // still sorry
                    try {
                        data = (Array<?>) attr.getValueObject(this, ui_state, "dynamic");
                    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                             NoSuchFieldException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (attr.name.equals("as")) { // still very sorry
                    try {
                        as = (String) attr.getValueObject(this, ui_state, "java.lang.String");
                    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                             NoSuchFieldException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            int data_len = data != null ? data.size : 0;
            for (int i = 0; i < data_len; i++) {
                for (GIXNode child : this.children) {
                    GIXNode new_child_node = child.duplicateWithoutState(this);
                    new_child_node.addContext(as, data.get(i)); // PERFORMANCE NOTE - this will create a new map for each new_child_node
                    new_child_node.children = new_child_node.getExpandedChildren();
                    new_children.add(new_child_node);
                }
            }
            return new_children;
        } else {
            for (GIXNode child : this.children) {
                child.children = child.getExpandedChildren();
            }
            return this.children;
        }
    }

    public Object getContextToRoot(String name) {
        // mostly for loop context
        if (this.context != null && this.context.containsKey(name)) {
            return this.context.get(name);
        } else if (this.parent != null) {
            return this.parent.getContextToRoot(name);
        }
        return null;
    }

    public GIXNode duplicateWithoutState(GIXNode parent) {
        GIXNode new_node = new GIXNode(ui_state, domNode, target, parent, attrs, constructor_attrs);
        if (children != null) {
            Array<GIXNode> new_children = new Array<>(children.size);
            for (GIXNode child : children) {
                new_children.add(child.duplicateWithoutState(new_node));
            }
            new_node.children = new_children;
        }
        return new_node;
    }

    public GIXAttr getAttr(String name) {
        for (GIXAttr attr : attrs) {
            if (attr.name.equals(name)) {
                return attr;
            }
        }
        return null;
    }
}

