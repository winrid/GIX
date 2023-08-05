package com.winricklabs.gix;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

public class If extends Actor {
    boolean is_shown = false;
    final GIXNode parent;

    public If(GIXNode parent) {
        this.parent = parent;
    }

    public void setIf(boolean is_shown) {
        this.is_shown = is_shown;
    }

    public void add(Actor actor) {
        if (this.is_shown) {
            if (parent.ui_instance instanceof Table) {
                ((Table) parent.ui_instance).add(actor);
            } else {
                ((Group) parent.ui_instance).addActor(actor);
            }
        }
    }
}
