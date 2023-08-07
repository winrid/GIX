package com.winricklabs.gix;


import com.badlogic.gdx.utils.ObjectMap;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;


public class GIXAttr {
    final private Object ui_data;
    final public String name;
    final private String value;

    private String explicit_class_path = null;
    public boolean is_constructor_arg = false;
    public String name_without_type;

    GIXAttr(Object ui_data, String name, String value) throws IllegalAccessException {
        this.ui_data = ui_data;
        this.name = name;
        this.value = value;
        String[] split = name.split(":");
        if (split.length == 1) {
//                throw new RuntimeException(String.format("Attribute %s is missing a type!", name));
            this.name_without_type = split[0];
            if (this.name_without_type.equals("new")) {
                this.is_constructor_arg = true;
            }
        } else if (split.length > 1) {
            if (split.length > 2) {
                throw new RuntimeException(String.format("Unsupported attribute %s - we only support one \":\" per attribute!", name));
            }
            String start = split[0].toLowerCase();
            if (start.equals("new")) {
                this.is_constructor_arg = true;
            } else {
                this.explicit_class_path = start;
                if (explicit_class_path.equals("string")) {
                    explicit_class_path = "java.lang.String";
                } else if (explicit_class_path.equals("int")) {
                    explicit_class_path = "java.lang.Integer";
                } else if (explicit_class_path.equals("double")) {
                    explicit_class_path = "java.lang.Double";
                } else if (explicit_class_path.equals("float")) {
                    explicit_class_path = "java.lang.Float";
                }
            }
            this.name_without_type = split[1];
        }
    }

    void setExplicitClassPath(String explicit_class_path) {
        this.explicit_class_path = explicit_class_path;
    }

    String getExplicitClassPath(GIXNode from_node) throws IllegalAccessException {
        if (explicit_class_path == null && value.startsWith("{") && value.endsWith("}")) {
            // TODO optimize - don't need actual value, just type.
            // or we can cache value, depending on how reactive stuff will work.
            Class<?> clazz = getClassFromModel(from_node, ui_data);
            if (clazz != null) {
                if (clazz.isAnonymousClass()) { // for event handlers in model
                    explicit_class_path = clazz.getAnnotatedSuperclass().getType().getTypeName();
                } else {
                    explicit_class_path = clazz.getName();
                }
            }
//            System.out.println("explicit_class_path is " + explicit_class_path);
        }
        return this.explicit_class_path;
    }

    Object getValueObject(GIXNode from_node, Object ui_data, String desired_class_path) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, NoSuchFieldException {
        switch (desired_class_path) {
            case "boolean":
            case "java.lang.Boolean":
                Object value = getValueFromModel(from_node, ui_data);
                if (value instanceof String) {
                    return value.equals("true");
                }
                return value;
            case "java.lang.CharSequence":
            case "java.lang.String":
                return getValueFromModel(from_node, ui_data);
            case "int":
            case "java.lang.Integer":
                return Integer.parseInt((String) getValueFromModel(from_node, ui_data));
            case "double":
            case "java.lang.Double":
                return Double.parseDouble((String) getValueFromModel(from_node, ui_data));
            case "float":
            case "java.lang.Float":
                return Float.parseFloat((String) getValueFromModel(from_node, ui_data));
            default:
                return getValueFromModel(from_node, ui_data);
//                    throw new RuntimeException(String.format("Do not yet support %s!", desired_class_path));
        }
    }

    private Object getValueFromModel(GIXNode from_node, Object ui_data) throws IllegalAccessException, NoSuchFieldException {
        if (ui_data == null) { // ui data will never be null and from node not be null, so this is a safe check/optimization
            return value;
        }
        if (value.startsWith("{") && value.endsWith("}")) {
            // TODO optimize string replacement
            String without_brackets = value.replace("{", "").replace("}", "");
            String[] path = without_brackets.split("\\.");
            Object from_context = from_node.getContextToRoot(path[0]);
            if (from_context != null) {
                if (path.length > 1) { // optimization
                    StringBuilder path_without_first = new StringBuilder();
                    for (int i = 1; i < path.length; i++) {
                        path_without_first.append(path[i]);
                        if (i != path.length - 1) {
                            path_without_first.append(".");
                        }
                    }
                    return getNestedProperty(from_context, path_without_first.toString());
                } else {
                    return from_context;
                }
            }
            return getNestedProperty(ui_data, without_brackets);
        }
        return value;
    }

    private static Object getNestedProperty(Object object, String path_string) throws NoSuchFieldException, IllegalAccessException {
        String[] path = path_string.split("\\.");
        for (String part : path) {
            object = getFieldValue(object, part);
        }
        return object;
    }


    private static Object getFieldValue(Object root, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Class<?> clazz = root.getClass();
        Field field = resolveField(clazz, fieldName);
        // have to restore the old value as it's only
        // for the Field object, not for the field itself
        return field.get(root);
    }

    private static Field resolveField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Field field;
        try {
            field = clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException nsfe) {
            Class<?> sc = clazz.getSuperclass();
            field = resolveField(sc, fieldName);
        }
        field.setAccessible(true); // Lets you work with private fields. You do not
        return field;
    }

    private static Class<?> getPropertyType(Class<?> clazz, String fieldName) {
        final String[] fieldNames = fieldName.split("\\.", -1);
        //if using dot notation to navigate for classes
        if (fieldNames.length > 1) {
            final String firstProperty = fieldNames[0];
            final String otherProperties =
                    StringUtils.join(fieldNames, '.', 1, fieldNames.length);
            final Class<?> firstPropertyType = getPropertyType(clazz, firstProperty);
            return getPropertyType(firstPropertyType, otherProperties);
        }

        try {
            return clazz.getDeclaredField(fieldName).getType();
        } catch (final NoSuchFieldException e) {
            if (!clazz.equals(Object.class)) {
                return getPropertyType(clazz.getSuperclass(), fieldName);
            }
            throw new IllegalStateException(e);
        }
    }

    private Class<?> getClassFromModel(GIXNode from_node, Object ui_data) throws IllegalAccessException {
        // TODO consolidate with getValueFromModel
        if (value.startsWith("{") && value.endsWith("}")) {
            // TODO optimize string replacement
            String without_brackets = value.replace("{", "").replace("}", "");
            String[] path = without_brackets.split("\\.");
            Object from_context = from_node.getContextToRoot(path[0]);
            if (from_context != null) {
                if (path.length > 1) { // optimization
                    StringBuilder path_without_first = new StringBuilder();
                    for (int i = 1; i < path.length; i++) {
                        path_without_first.append(path[i]);
                        if (i != path.length - 1) {
                            path_without_first.append(".");
                        }
                    }
                    return getPropertyType(from_context.getClass(), path_without_first.toString());
                } else {
                    return from_context.getClass();
                }
            }
            return getPropertyType(ui_data.getClass(), without_brackets);
        }
        return value.getClass();
    }


    private static class PrimitiveTypeMapper {
        private final ObjectMap<String, String> mapping = new ObjectMap<>();
        private static PrimitiveTypeMapper instance;

        PrimitiveTypeMapper() {
            mapping.put("boolean", "java.lang.Boolean");
            mapping.put("integer", "java.lang.Integer");
            mapping.put("double", "java.lang.Double");
            mapping.put("float", "java.lang.Float");
        }

        public static PrimitiveTypeMapper getInstance() {
            if (instance == null) {
                instance = new PrimitiveTypeMapper();
            }
            return instance;
        }

        public Boolean maps(String from, String to) {
            PrimitiveTypeMapper primitiveTypeMapper = PrimitiveTypeMapper.getInstance();
            String actual_to = primitiveTypeMapper.mapping.get(from);
            if (actual_to != null) {
                return actual_to.equals(from);
            }
            String actual_to_swapped = primitiveTypeMapper.mapping.get(to);
            if (actual_to_swapped != null) {
                return actual_to_swapped.equals(from);
            }
            return null;
        }

    }

    boolean matchesType(GIXNode from_node, String other_class_path) throws IllegalAccessException {
        String explicit_class_path = getExplicitClassPath(from_node);
        if (explicit_class_path != null) {
            if (this.explicit_class_path.equals(other_class_path)
                    || (other_class_path.equals("java.lang.CharSequence") && this.explicit_class_path.equals("java.lang.String"))) {
                return true;
            }
            Boolean does_map_primitive = PrimitiveTypeMapper.getInstance().maps(other_class_path, explicit_class_path);
            if (does_map_primitive != null) {
                return does_map_primitive;
            }
            try {
                // todo optimize
                return Class.forName(other_class_path).isAssignableFrom(Class.forName(explicit_class_path));
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            switch (other_class_path) {
                case "boolean":
                    return this.value.equals("true") || this.value.equals("false");
                case "java.lang.CharSequence":
                case "java.lang.String":
                    return true;
                case "int":
                case "java.lang.Integer":
                    try { // should be faster way to do this
                        Integer.parseInt(this.value);
                        return true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                case "double":
                case "java.lang.Double":
                    try { // should be faster way to do this
                        Double.parseDouble(this.value);
                        return true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                case "float":
                case "java.lang.Float":
                    try { // should be faster way to do this
                        Float.parseFloat(this.value);
                        return true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                default:
                    return false;
            }
        }
    }

    public String getRawValue() {
        return this.value;
    }

}
