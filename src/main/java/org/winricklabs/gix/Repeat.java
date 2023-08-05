package com.winricklabs.gix;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Array;

public class Repeat extends Actor {
    Array<?> data;
    String property_name;
    final GIXNode parent;

    public Repeat(GIXNode parent) {
        this.parent = parent;
    }

    public void setWith(Array<?> data) {
        this.data = data;
    }

    public void setAs(String property_name) {
        this.property_name = property_name;
    }

    public void add(Actor actor) {
        if (parent.ui_instance instanceof Table) {
            ((Table) parent.ui_instance).add(actor);
        } else {
            ((Group) parent.ui_instance).addActor(actor);
        }
    }
}
