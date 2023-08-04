package org.winricklabs.gix;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

public class Not {
    boolean is_shown = false;
    final GIXNode parent;

    public Not(GIXNode parent) {
        this.parent = parent;
    }

    public void setIf(boolean value) {
        this.is_shown = !value;
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
