package org.winricklabs.gix;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.parser.ParseSettings;
import org.jsoup.parser.Parser;
import org.jsoup.select.NodeVisitor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;

// "gdx-in-html"

public class GIXComponent<Model> {
    private static final Array<GIXComponent<?>> instances = new Array<>(); // for easy hot reloading api
    Array<String> class_paths = new Array<>(2);
    GIXParent parent;
    FileHandle file;
    FileHandle absolute_file_handle;
    String data;
    Actor tree = null;
    GIXNode gix_root = null;
    ObjectMap<String, Actor> actorsById = new ObjectMap<>();
    private static boolean dev_mode = false;
    Model state = null;
    private long last_hot_reload_time_ms = 0;

    public GIXComponent(GIXParent parent, FileHandle file, Array<String> additional_class_paths) {
        this.parent = parent;
        this.file = file;
        if (dev_mode) {
            // TODO cross platform support
            absolute_file_handle = Gdx.files.absolute(Gdx.files.getLocalStoragePath() + "/assets/" + file.path());
        }
        data = file.readString();
        class_paths.addAll(
                "org.winricklabs.gix.",
                "com.kotcrab.vis.ui.layout.",
                "com.kotcrab.vis.ui.widget.",
                "com.kotcrab.vis.ui.widget.spinner.",
                "com.badlogic.gdx.scenes.scene2d.ui."
        );
        if (additional_class_paths != null) {
            class_paths.addAll(additional_class_paths);
        }
        instances.add(this);
    }

    public GIXComponent(GIXParent parent, FileHandle file) {
        this(parent, file, null);
    }

    GIXComponent<Model> withState(Model state) {
        this.state = state;
        return this;
    }

    GIXComponent<Model> setState(Model state) {
        this.state = state;
        parse();
        return this;
    }

    static void setDevMode(boolean dev_mode) {
        GIXComponent.dev_mode = dev_mode;
    }

    public static void tickComponents() {
        if (dev_mode) {
            for (GIXComponent<?> instance : instances) {
                instance.tick();
            }
        }
    }

    public void tick() {
        if (dev_mode) {
            long now = System.currentTimeMillis();
            if (now - last_hot_reload_time_ms > 100) {
                String new_data = absolute_file_handle.readString();
                if (!new_data.equals(data)) {
                    Gdx.app.log("GIX", "Reloading Template " + absolute_file_handle.name());
                    data = new_data;
                    parse();
                    long end = System.currentTimeMillis();
                    Gdx.app.log("GIX", String.format("Reloaded in %sms.", end - now));
                }
                this.last_hot_reload_time_ms = now;
            }
        }
    }

    GIXComponent<Model> parse() {
        long start = System.currentTimeMillis();
        Parser parser = Parser.htmlParser();
        parser.setTrackPosition(dev_mode);
        parser.settings(new ParseSettings(true, true));
        Document doc = parser.parseInput(data, "/"); // TODO cache for state changes

        try {
            if (tree != null) {
                gix_root = null;
                tree = null;
                actorsById.clear();
            }
            doc.traverse(new NodeVisitor() {

                GIXNode current_node = null;
                final IntMap<GIXNode> last_node_at_depth = new IntMap<>();

                public void head(Node node, int depth) {
                    String node_name = node.nodeName();
                    if (shouldSkipNode(node_name)) {
                        return;
                    }
                    System.out.println("Entering tag: " + node.nodeName() + " " + depth);
                    try {
                        GIXNode last_at_prev_depth = last_node_at_depth.get(depth - 1);
                        GIXNodeTarget gixNodeTarget = GIXNodeTarget.fromNodeName(last_at_prev_depth, node_name, class_paths);

                        Array<GIXAttr> attrs = new Array<>(3);
                        Array<GIXAttr> constructor_attrs = new Array<>(0);
                        for (Iterator<Attribute> it = node.attributes().iterator(); it.hasNext(); ) {
                            Attribute attr = it.next();
                            GIXAttr gix_GIX_attr = new GIXAttr(state, attr.getKey(), attr.getValue());
                            if (gix_GIX_attr.is_constructor_arg) {
                                constructor_attrs.add(gix_GIX_attr);
                            } else {
                                attrs.add(gix_GIX_attr);
                            }
                        }
                        current_node = new GIXNode(state, node, gixNodeTarget, last_at_prev_depth, attrs, constructor_attrs);
                        if (gix_root == null) {
                            gix_root = current_node;
                        }
                        if (last_at_prev_depth != null) {
                            last_at_prev_depth.addChild(current_node);
                        }
                        last_node_at_depth.put(depth, current_node);
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                        throw new NodeException(node, e);
                    } catch (IllegalAccessException e) {
                        throw new NodeException(node, e);
                    }
                }

                public void tail(Node node, int depth) {
//                String node_name = node.nodeName();
//                if (shouldSkipNode(node_name)) {
//                    return;
//                }
//                System.out.println("Exiting tag: " + node.nodeName());
                }

                private boolean shouldSkipNode(String name) {
                    return name.startsWith("#") || (
                            name.length() < 5 // optimization
                                    && (name.equals("html") || name.equals("head") || name.equals("body"))
                    ); // #text, #comment, #document
                }
            });

            // find loops, expand.
            gix_root.expandLoops();

            System.out.println("parse time: " + (System.currentTimeMillis() - start));
            start = System.currentTimeMillis();
            Array<GIXNode> pending_actors_nodes = new Array<>(0);
            // we rarely need to store constructors on each node, so we use a separate map, vs the overhead of the property on every GSXNode.
            ObjectMap<GIXNode, GIXMatchingConstructor> pending_node_constructors = new ObjectMap<>();
            gix_root.iterate((node) -> {
                try {
                    if (node.target.method != null) {
                        GIXNode target = node;
                        // is method, not constructor
                        // this is for table cells - we skip a level and go from VisTable:row node up to the VisTable node
                        // can't assume it's one level up - for example where a <Repeat> is between content and <Table>
                        while (true) {
                            try {
                                target.parent.ui_instance.getClass().getMethod(node.target.method.getName());
                                node.setUIInstance(node.target.method.invoke(target.parent.ui_instance), actorsById);
                                break;
                            } catch (NoSuchMethodException ignored) {
                                target = target.parent;
                            }
                        }
                        if (node.ui_instance != null) { // maybe should throw if null?
                            invokeSettersAndMethods(node, node.ui_instance, node.attrs);
                        }
                        // we don't call addUINode here because when we invoke the method (prev line)
                        // we are already adding the node to the tree. For example, it's doing:
                        // new Table().cell()
                        // in which case, we don't need to pass the cell back to the table - the table
                        // already knows about it.
                        // whereas, addUINode is for managing parents and invoking methods via attributes
                    } else {
                        Constructor<?>[] constructors = node.target.clazz.getConstructors(); // TODO BENCHMARK CACHE?
                        /*
                          If we find no matching constructors, does the node accept actor arguments? Then keep walking and then come back later.
                           store a reference to this instance, add to tree but don't add to parent node yet
                           construct children until done
                           then return, actually construct instance with children parameters, and then add to parent node
                         */
                        GIXMatchingConstructor matching_constructor = getMatchingConstructor(node, constructors, node.constructor_attrs);
                        if (matching_constructor != null) {
                            Array<Object> raw_values = new Array<>(matching_constructor.matching_attributes != null ? matching_constructor.matching_attributes.size : 0);
                            if (matching_constructor.matching_attributes != null) {
                                for (GIXAttr matching_GIX_attr : matching_constructor.matching_attributes) {
                                    raw_values.add(matching_GIX_attr.getValueObject(node, state, matching_GIX_attr.getExplicitClassPath(node)));
                                }
                            }
                            if (matching_constructor.requires_actors) {
                                // will load on second pass
                                pending_actors_nodes.add(node);
                                pending_node_constructors.put(node, matching_constructor);
                                // TODO support root node as requiring actors
                            } else if (matching_constructor.requires_parent_gix_node) {
                                System.out.printf("Calling %s \n", matching_constructor.constructor.getName());
                                raw_values.insert(0, node.parent);
                                Object instance = matching_constructor.constructor.newInstance(raw_values.toArray());
                                addUINode(node, instance, false);
                            } else {
                                System.out.printf("Calling %s \n", matching_constructor.constructor.getName());
                                Object instance = matching_constructor.constructor.newInstance(raw_values.toArray());
                                addUINode(node, instance, true);
                            }
                        } else {
                            throw new RuntimeException("No matching constructors found for " + node.name + "! Are you missing some attributes?");
                        }
                    }
                } catch (Exception e) {
                    throw new NodeException(node.domNode, e);
                }
            });
            for (GIXNode pending_actor : pending_actors_nodes) {
                if (pending_actor.children.size == 0) {
                    throw new NodeException(pending_actor.domNode, new RuntimeException(String.format("Node %s expects children, but none found.", pending_actor.name)));
                }
                Array<Object> raw_values = new Array<>(pending_actor.children.size);
                for (GIXNode node : pending_actor.children) {
                    raw_values.add(node.ui_instance);
                }
                GIXMatchingConstructor actor_constructor = pending_node_constructors.get(pending_actor);
                try {
                    if (actor_constructor.matching_attributes != null) {
                        for (GIXAttr matching_GIX_attr : actor_constructor.matching_attributes) {
                            raw_values.add(matching_GIX_attr.getValueObject(pending_actor, state, matching_GIX_attr.getExplicitClassPath(pending_actor)));
                        }
                    }
                    System.out.printf("Calling (2nd pass) %s \n", pending_actor.name);
                    Object instance = actor_constructor.constructor.newInstance(raw_values.toArray());
                    addUINode(pending_actor, instance, true);
                } catch (Exception e) {
                    throw new NodeException(pending_actor.domNode, e);
                }
            }

        } catch (NodeException e) {
            // todo hard fail option
            throw new RuntimeException(e.exception);
//            Label.LabelStyle labelStyle = new Label.LabelStyle(new BitmapFont(), Color.RED);
//            e.node.wrap("<ERROR>" + e.exception.getMessage() + ":</ERROR>");
//            Label label = new Label(e.node.root().outerHtml(), labelStyle);
//            label.setFillParent(true);
//            root = label;
        }
        System.out.println("step 2 time: " + (System.currentTimeMillis() - start));
        parent.addOrReplace(tree);
        return this;
    }

    private static final class NodeException extends RuntimeException {
        private final Node node;
        private final Throwable exception;

        NodeException(Node node, Throwable exception) {
            this.node = node;
            this.exception = exception;
        }
    }

    void addUINode(GIXNode node, Object instance, boolean add_to_parent) throws InvocationTargetException, IllegalAccessException {
        node.setUIInstance(instance, actorsById);
        invokeSettersAndMethods(node, instance, node.attrs);
        if (tree == null) {
            tree = (Actor) instance;
        } else if (add_to_parent) {
            addNodeToParent(node, instance);
        }
    }

    void addNodeToParent(GIXNode node, Object instance) throws InvocationTargetException, IllegalAccessException {
        if (node.parent == null) {
            return;
        }
        if (node.parent.target.clazz == null) {
            // is method, not constructor
            // this is for table cells - we skip a level and go from VisTable:row node up to the VisTable node
            // can't assume it's one level up - for example where a <Repeat> is between content and <Table>
            do {
                node = node.parent;
            } while (node.parent.target.clazz == null);
        }
        boolean found = false;
        while (!found) {
            Method[] parent_methods = node.parent.target.clazz.getMethods();
            for (Method method : parent_methods) {
                if (method.getName().equals("add") || method.getName().equals("append") || method.getName().equals("addChild") || method.getName().equals("addActor")) {
                    if (method.getParameterTypes().length == 1 && method.getParameterTypes()[0].getName().endsWith("Actor")) {
                        method.invoke(node.parent.ui_instance, instance);
                        found = true;
                        break;
                    }
                }
            }
            if (node.parent != null) {
                node = node.parent;
            } else {
                break;
            }
        }
    }

    private static GIXMatchingConstructor getMatchingConstructor(GIXNode node, Constructor<?>[] constructors, Array<GIXAttr> attrs) throws IllegalAccessException {
        GIXMatchingConstructor best_match = null;
        int best_match_argument_count = 0;
        for (Constructor<?> constructor : constructors) {
            Class<?>[] type_variables = constructor.getParameterTypes();
            if (type_variables.length == 0 && attrs.size == 0) {
                return new GIXMatchingConstructor(constructor);
            } else if (type_variables.length <= attrs.size) {
                Array<GIXAttr> matching_attributes = new Array<>(0);
                boolean mismatch = false;
                for (int type_variable_idx = 0; !mismatch && type_variable_idx < type_variables.length; type_variable_idx++) {
                    Class<?> type_variable = type_variables[type_variable_idx];
                    GIXAttr GIXAttr_maybe = attrs.get(type_variable_idx);
                    if (GIXAttr_maybe.is_constructor_arg
                            && GIXAttr_maybe.matchesType(node, type_variable.getName())
                    ) {
                        GIXAttr_maybe.setExplicitClassPath(type_variable.getName());
                        matching_attributes.add(GIXAttr_maybe);
                    } else {
                        mismatch = true;
                    }
                }
                if (matching_attributes.size == type_variables.length) {
                    if (matching_attributes.size > best_match_argument_count) {
                        best_match = new GIXMatchingConstructor(constructor, matching_attributes);
                        best_match_argument_count = matching_attributes.size;
                    }
                }
            }
        }
        // look for a constructor that takes Actors
        if (best_match == null) {
            for (Constructor<?> constructor : constructors) {
                for (Class<?> type : constructor.getParameterTypes()) {
                    if (!type.getName().endsWith("Actor")) {
                        continue;
                    }
                    Class<?>[] type_variables = constructor.getParameterTypes();
                    Array<GIXAttr> matching_attributes = new Array<>(0);
                    int idx_after_actors = 0;
                    int num_matching_params = 0;
                    boolean mismatch = false;
                    for (int type_variable_idx = 0; !mismatch && type_variable_idx < type_variables.length; type_variable_idx++) {
                        Class<?> type_variable = type_variables[type_variable_idx];
                        if (type_variable.getName().endsWith("Actor")) {
                            num_matching_params++;
                            continue;
                        }
                        if (idx_after_actors == attrs.size) {
                            mismatch = true;
                            continue;
                        }
                        GIXAttr GIXAttr_maybe = attrs.get(idx_after_actors);
                        if (GIXAttr_maybe.is_constructor_arg
                                && GIXAttr_maybe.matchesType(node, type_variable.getName())
                        ) {
                            GIXAttr_maybe.setExplicitClassPath(type_variable.getName());
                            matching_attributes.add(GIXAttr_maybe);
                            num_matching_params++;
                            idx_after_actors++;
                        } else {
                            mismatch = true;
                        }
                    }
                    if (num_matching_params == type_variables.length) {
                        if (num_matching_params > best_match_argument_count) {
                            best_match = new GIXMatchingConstructor(constructor, matching_attributes, true);
                            best_match_argument_count = matching_attributes.size;
                        }
                    }
                }
            }
        }
        // look for a constructor that takes a GIXNode
        if (best_match == null) {
            for (Constructor<?> constructor : constructors) {
                for (Class<?> type : constructor.getParameterTypes()) {
                    if (!type.getName().endsWith("GIXNode")) {
                        continue;
                    }
                    Class<?>[] type_variables = constructor.getParameterTypes();
                    Array<GIXAttr> matching_attributes = new Array<>(0);
                    int idx_after_actors = 0;
                    int num_matching_params = 0;
                    boolean mismatch = false;
                    for (int type_variable_idx = 0; !mismatch && type_variable_idx < type_variables.length; type_variable_idx++) {
                        Class<?> type_variable = type_variables[type_variable_idx];
                        if (type_variable.getName().endsWith("GIXNode")) {
                            num_matching_params++;
                            continue;
                        }
                        if (idx_after_actors == attrs.size) {
                            mismatch = true;
                            continue;
                        }
                        GIXAttr GIXAttr_maybe = attrs.get(idx_after_actors);
                        if (GIXAttr_maybe.is_constructor_arg
                                && GIXAttr_maybe.matchesType(node, type_variable.getName())
                        ) {
                            GIXAttr_maybe.setExplicitClassPath(type_variable.getName());
                            matching_attributes.add(GIXAttr_maybe);
                            num_matching_params++;
                            idx_after_actors++;
                        } else {
                            mismatch = true;
                        }
                    }
                    if (num_matching_params == type_variables.length) {
                        if (num_matching_params > best_match_argument_count) {
                            best_match = new GIXMatchingConstructor(constructor, matching_attributes, false, true);
                            best_match_argument_count = matching_attributes.size;
                        }
                    }
                }
            }
        }
        return best_match;
    }

    private void invokeSettersAndMethods(GIXNode node, Object instance, Array<GIXAttr> attrs) throws IllegalAccessException {
        Method[] methods = instance.getClass().getMethods();
        for (GIXAttr GIXAttr : attrs) {
            if (!invokeMethod(instance, methods, GIXAttr)) {
                if (!invokeSetter(node, state, instance, methods, GIXAttr)) {
                    System.err.printf("Warning! Did not find method %s to invoke with value %s. Note that we don't support setters with more than one argument.\n", GIXAttr.name_without_type, GIXAttr.getRawValue());
                }
            }
        }
    }

    private static boolean invokeMethod(Object instance, Method[] methods, GIXAttr gixAttr) {
        System.out.println("invokeMethod " + instance + " " + gixAttr.name_without_type);
        for (Method method : methods) {
            if (method.getName().equals(gixAttr.name_without_type)) {
                // TODO support arguments.
                if (method.getParameterTypes().length == 0) {
                    try {
                        method.invoke(instance);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean invokeSetter(GIXNode node, Object ui_data, Object instance, Method[] methods, GIXAttr GIXAttr) throws IllegalAccessException {
        String attr_name = GIXAttr.name_without_type;
        String attr_name_capitalized = attr_name.substring(0, 1).toUpperCase() + attr_name.substring(1);
        String setter_name = "set" + attr_name_capitalized;
        for (Method method : methods) {
            String name = method.getName();
            if (name.equals(setter_name) || name.equals(attr_name)) {
                // TODO support more than one argument.
                Class<?>[] parameter_types = method.getParameterTypes();
                if (parameter_types.length == 1 && GIXAttr.matchesType(node, parameter_types[0].getName())) {
                    try {
                        method.invoke(instance, GIXAttr.getValueObject(node, ui_data, parameter_types[0].getName()));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    return true;
                } else {
//                    System.err.println("not applicable " + parameter_types[0].getName() + " for " + attr.value);
                }
            }
        }
        return false;
    }

    Actor getRoot() {
        return tree;
    }

    Actor getById(String id) {
        return actorsById.get(id);
    }
}
