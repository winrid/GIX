# GIX
### LibGDX in XHTML

GIX is a high-productivity way to write Scene2D layouts and components.
You write the layout, some logic, and data binding in HTML. The reason for this
is that productivity is much higher for some types of projects. GIX supports a component system and **hot reloading**.

This means that you can edit your views and save, and the game, or UI, is automatically updated
without a restart. This only applies to the HTML - the components' Java code is not hot reloaded (yet).

Templates are "type safe" in that they are validated at parse time. You can't pass an object to
a `Label` constructor and have it silently fail - you get a nice error message. You can then fix, save,
and continue - without restarting.

All LibGDX and vis-ui components should be supported (see `How it Works`). This also means you can write
any Java class that extends `Actor` and it can become a `<Component>` used in your template!

## Todo App Example

Let's write our template:

```html
<VisWindow new="Todo App" setFillParent="true">
    <VisTable>
        <Repeat with={todos} as="todo">
            <VisTable:row>
                <VisLabel new={todo.name}></VisLabel>
                <FlowGroup new="false">
                    <VisTextButton new="Delete" addListener={todo.deleteListener}></VisTextButton>
                </FlowGroup>
            </VisTable:row>
        </Repeat>
    </VisTable>
    <VisTable>
        <VisTextField id="field"></VisTextField>
        <VisTextButton new="Add" addListener={addTodoListener}></VisTextButton>
    </VisTable>
</VisWindow>
```

We're using `vis-ui` here but regular `Scene2D` components work too.

Now let's write a corresponding class which will define the state passed to this template:

```java
public class TodoComponent extends GIXComponent<TodoComponent.TodoState> {

    public TodoComponent(GIXParent parent) {
        super(parent, Gdx.files.internal("todo.html"));
        setState(new TodoState());
    }

    public class TodoState {
        Array<Todo> todos = Array.with(new Todo("Buy Eggs"), new Todo("Write Java"));
        ClickListener addTodoListener = new ClickListener() {
            public void clicked(InputEvent event, float x, float y) {
                VisTextField field = ((VisTextField) getById("field"));
                todos.add(new Todo(field.getText()));
                field.setText("");
                setState(TodoState.this);
            }
        };

        public class Todo {
            public String name;

            Todo(String name) {
                this.name = name;
            }

            ClickListener deleteListener = new ClickListener() {
                public void clicked(InputEvent event, float x, float y) {
                    todos.removeValue(Todo.this, true);
                    setState(TodoState.this);
                }
            };
        }
    }
}
```

Now let's use it (just add to stage, but GIXParent can also accept another component):

```java
public class TodoApp extends ApplicationAdapter {
    private Stage stage;

    @Override
    public void create() {
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        GIXComponent.addComponentClassPaths('com.yourapp.components'); // what package will your components be in?
        GIXComponent.setDevMode(true); // for hot reload
        new TodoComponent(new GIXParent(stage));
    }

    @Override
    public void render() {
        ScreenUtils.clear(1, 1, 1, 1);
        stage.act(Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f));
        GIXComponent.tickComponents(); // for hot reload
        stage.draw();
    }

}
```

## Child Components

Children work this way:

```java
public class ChildComponent extends GIXComponent<ChildComponent.ChildState> {

    public ChildComponent(GIXParent parent) {
        super(parent, Gdx.files.internal("child.html"));
        setState(new ChildState());
    }

    public ChildComponent(GIXNode parent) {
        this(new GIXParent(parent));
    }

    public class ChildState {

    }
    
    public void setCustomData(boolean someData) {
        // do something with data from parent
    }
}
```

Now you can use `<ChildComponent customData="true"></ChildComponent>` in the parent.

## How it Works

- XHTML tags correspond to a Class.
- Tag attributes become method calls/setters.
- Children are added to parents by searching for an `add()` or `addActor()` method on the parent.
- Quoted values are passed literally (cast/mapped to correct type based on target).
- Values `{like_this}` come from the passed in state. Expressions are not supported - only values. This keeps things simple and faster.
- Table rows etc can be used via `<Table:row></Table:row>` notation.
- `setState()` re-renders the whole component.
- For more CPU-intensive work you can manipulate components directly by calling `getById()` instead of `setState()` all the time. For example, for a large data table, you probably don't want to re-render the whole table when adding an item.

## Installation

The library is not yet on Maven. Soon! "Statically link" it :)

## Contributing

Contributions welcome! Some low-hanging fruit I could get help with:

- We need a runnable demo project in this repo.
- Integration tests (define HTML, assert resulting view structure. I would suggest a snapshot approach like Jest snapshots - turn the resulting UI into a JSON tree).
- Performance optimizations.
- Performance tests.
- Any "TODOs" I've put in the code in the initial release.
- Hot reloading templates does not work on mobile yet. It only works on Desktop.

## Commercial Support

I offer paid development support and feature/bugfix priority. Contact: winrid [at] gmail.com. 

## License

Apache-2.0
