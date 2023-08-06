package widgets;

import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;

public class ConstructorTestingWidget extends WidgetGroup {
    public boolean testFieldA = false;
    public boolean testFieldB = false;

    public ConstructorTestingWidget(boolean testFieldA) {
        super();
        this.testFieldA = testFieldA;
    }

    public ConstructorTestingWidget(boolean testFieldA, boolean testFieldB) {
        super();
        this.testFieldA = testFieldA;
        this.testFieldB = testFieldB;
    }
}
