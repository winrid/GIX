package com.winricklabs.gix;

import com.badlogic.gdx.utils.Array;

import java.lang.reflect.Method;

public class GIXNodeTarget {

    final Class<?> clazz;
    final Method method;

    GIXNodeTarget(Class<?> clazz, Method method) {
        this.clazz = clazz;
        this.method = method;
    }

    public static GIXNodeTarget fromNodeName(GIXNode parent, String name, Array<String> class_paths) {
        Class<?> clazz = findClass(name, class_paths);
        Method method = null;
        if (clazz == null) {
            if (name.contains(":")) {
                // see if method of parent
                if (parent != null) {
                    while (true) {
                        String method_name = name.split(":")[1];
                        Method[] methods = parent.target.clazz.getMethods();
                        for (Method potential_method : methods) {
                            if (potential_method.getName().equals(method_name)) {
                                method = potential_method;
                                break;
                            }
                        }
                        if (method != null) {
                            break;
                        } else {
                            if (parent.parent != null) {
                                parent = parent.parent; // could be: Table -> Repeat -> Table:Row -> Node
                            } else {
                                break;
                            }
                        }
                    }
                    if (method == null) {
                        throw new RuntimeException("Could not find method to call: " + name);
                    }
                } else {
                    throw new RuntimeException("Could not find class to load (has colon in name but no parent to reference): " + name);
                }
            } else {
                throw new RuntimeException("Could not find class to load: " + name);
            }
        }
        return new GIXNodeTarget(clazz, method);
    }

    static Class<?> findClass(String name, Array<String> class_paths) {
        for (String class_path : class_paths) {
            String class_to_load = class_path + name;
            Class<?> clazz = null;
            try {
                clazz = Class.forName(class_to_load); // TODO BENCHMARK CACHE?
                return clazz;
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }
}
