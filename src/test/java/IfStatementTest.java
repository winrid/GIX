import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.winricklabs.gix.GIXComponent;
import com.winricklabs.gix.GIXParent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import widgets.TestWidget;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class IfStatementTest {

    @BeforeAll
    public static void setup() {
        GIXComponent.addComponentClassPaths("widgets");
    }

    static class TestingComponent extends GIXComponent<TestingComponent.TestingComponentData> {
        TestingComponent(GIXParent parent) {
            super(parent, new FileHandle(new File("if_test.html").getAbsolutePath()));
        }

        static class TestingComponentData {
            public boolean true_primitive = true;
            public Boolean true_obj = Boolean.TRUE;
            public Boolean true_obj_null = null;
            public boolean false_primitive = false;

            public TestingComponentData() {

            }
        }
    }

    @Test
    public void ifStatementTest() {
        WidgetGroup parent = new WidgetGroup();
        TestingComponent component = new TestingComponent(new GIXParent(parent));
        component.setState(new TestingComponent.TestingComponentData());
        assertEquals(2, component.getRoot().getChildren().size);
        TestWidget true_primitive = (TestWidget) component.getById("true_primitive");
        TestWidget true_obj = (TestWidget) component.getById("true_obj");
        TestWidget true_obj_null = (TestWidget) component.getById("true_obj_null");
        TestWidget false_primitive = (TestWidget) component.getById("false_primitive");
        assertNotNull(true_primitive);
        assertNotNull(true_obj);

        assertTrue(component.getRoot().getChildren().contains(true_primitive, true));
        assertTrue(component.getRoot().getChildren().contains(true_obj, true));
        assertFalse(component.getRoot().getChildren().contains(true_obj_null, true));
        assertFalse(component.getRoot().getChildren().contains(false_primitive, true));

        // TODO
//        assertNull(true_obj_null);
//        assertNull(false_primitive);
    }
}
