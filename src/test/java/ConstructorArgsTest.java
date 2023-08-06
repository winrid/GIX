import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.winricklabs.gix.GIXComponent;
import com.winricklabs.gix.GIXParent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import widgets.ConstructorTestingWidget;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class ConstructorArgsTest {

    @BeforeAll
    public static void setup() {
        GIXComponent.addComponentClassPaths("widgets");
    }

    static class TestingComponent extends GIXComponent<TestingComponent.TestingComponentData> {
        TestingComponent(GIXParent parent) {
            super(parent, new FileHandle(new File("constructor_arg_test.html").getAbsolutePath()));
        }

        static class TestingComponentData {
            public TestingComponentData() {

            }
        }
    }

    @Test
    public void constructorArgsTest() {
        WidgetGroup parent = new WidgetGroup();
        TestingComponent component = new TestingComponent(new GIXParent(parent));
        component.setState(new TestingComponent.TestingComponentData());
        assertEquals(2, component.getRoot().getChildren().size);
        ConstructorTestingWidget widgetOne = (ConstructorTestingWidget) component.getById("single-constructor-arg");
        ConstructorTestingWidget widgetTwo = (ConstructorTestingWidget) component.getById("multi-constructor-arg");
        assertTrue(widgetOne.testFieldA);
        assertFalse(widgetOne.testFieldB);

        assertTrue(widgetTwo.testFieldA);
        assertTrue(widgetTwo.testFieldB);
    }
}
