package org.winricklabs.gix;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;

public class GIXParent {
    // do java programmers dream of electric unions?
    private final WidgetGroup group;
    private final Stage stage;
    private Actor existing;

    public GIXParent(WidgetGroup group) {
        this.group = group;
        this.stage = null;
    }

    public GIXParent(GIXNode node) {
        this.group = (WidgetGroup) node.ui_instance;
        this.stage = null;
    }

    public GIXParent(Stage stage) {
        this.group = null;
        this.stage = stage;
    }

    public void addOrReplace(Actor actor) {
        if (existing == null) {
            if (group != null) {
                group.addActor(actor);
            } else if (this.stage != null) {
                this.stage.addActor(actor);
            }
        } else {
            if (group != null) {
                group.addActorAfter(existing, actor);
            } else if (this.stage != null) {
                this.stage.getRoot().addActorAfter(existing, actor);
            }
            existing.remove();
        }
        existing = actor;
    }

}
