package com.winricklabs.gix;


import java.lang.reflect.Field;

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
            System.out.println("explicit_class_path is " + explicit_class_path);
        }
        return this.explicit_class_path;
    }

    Object getValueObject(GIXNode from_node, Object ui_data, String desired_class_path) throws IllegalAccessException {
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

    private Object getValueFromModel(GIXNode from_node, Object ui_data) throws IllegalAccessException {
        if (ui_data == null) { // ui data will never be null and from node not be null, so this is a safe check/optimization
            return value;
        }
        if (value.startsWith("{") && value.endsWith("}")) {
            FieldHelper fieldHelper = new FieldHelper();
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
                    return fieldHelper.getFieldValue(from_context, path_without_first.toString());
                } else {
                    return from_context;
                }
            }
            return fieldHelper.getFieldValue(ui_data, without_brackets);
        }
        return value;
    }

    private Class<?> getClassFromModel(GIXNode from_node, Object ui_data) throws IllegalAccessException {
        // TODO consolidate with getValueFromModel
        if (value.startsWith("{") && value.endsWith("}")) {
            FieldHelper fieldHelper = new FieldHelper();
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
                    return fieldHelper.getFieldClass(from_context, path_without_first.toString());
                } else {
                    return from_context.getClass();
                }
            }
            return fieldHelper.getFieldClass(ui_data, without_brackets);
        }
        return value.getClass();
    }

    boolean matchesType(GIXNode from_node, String other_class_path) throws IllegalAccessException {
        String explicit_class_path = getExplicitClassPath(from_node);
        if (explicit_class_path != null) {
            if (this.explicit_class_path.equals(other_class_path)
                    || (other_class_path.equals("java.lang.CharSequence") && this.explicit_class_path.equals("java.lang.String"))) {
                return true;
            }
            switch (other_class_path) {
                case "boolean":
                    return explicit_class_path.equals("java.lang.Boolean");
                case "int":
                    return explicit_class_path.equals("java.lang.Integer");
                case "double":
                    return explicit_class_path.equals("java.lang.Double");
                case "float":
                    return explicit_class_path.equals("java.lang.Float");
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


    // TODO the methods here should be able to be static so we don't have to
    // create a new instance of "FieldHelper"
    private static class FieldHelper {

        /**
         * get a Field including superclasses
         */
        public Field getField(Class<?> c, String fieldName) {
            Field result = null;
            if (c == null) {
                return null;
//                throw new RuntimeException("Could not find \"" + fieldName + "\" in model");
            }
            try {
                result = c.getDeclaredField(fieldName);
            } catch (NoSuchFieldException nsfe) {
                Class<?> sc = c.getSuperclass();
                result = getField(sc, fieldName);
            }
            return result;
        }

        /**
         * set a field Value by name
         */
        public void setFieldValue(Object target, String fieldName, Object value) throws IllegalAccessException {
            Class<? extends Object> c = target.getClass();
            Field field = getField(c, fieldName);
            field.setAccessible(true);
            // beware of ...
            // http://docs.oracle.com/javase/tutorial/reflect/member/fieldTrouble.html
            field.set(this, value);
        }

        /**
         * get a field Value by name
         */
        public Object getFieldValue(Object target, String fieldName) throws IllegalAccessException {
            Class<? extends Object> c = target.getClass();
            Field field = getField(c, fieldName);
            if (field != null) {
                field.setAccessible(true);
                Object result = field.get(target);
                return result;
            }
            return null;
        }

        /**
         * get a field Value by name
         */
        public Class<?> getFieldClass(Object target, String fieldName) throws IllegalAccessException {
            Class<? extends Object> c = target.getClass();
            Field field = getField(c, fieldName);
            if (field != null) {
                field.setAccessible(true);
                Object value = field.get(target);
                if (value != null) {
                    return value.getClass();
                } else {
                    Class<?> type = field.getType();
                    return type;
                }
            }
            return null;
        }

    }
}
