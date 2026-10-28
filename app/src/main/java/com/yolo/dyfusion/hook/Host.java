package com.yolo.dyfusion.hook;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

final class Host {
  private static final ConcurrentHashMap<Class<?>, Map<String, Field>> fields =
      new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<Class<?>, List<Method>> methods =
      new ConcurrentHashMap<>();

  private static Map<String, Field> fields(Class<?> type) {
    return fields.computeIfAbsent(
        type,
        t -> {
          Map<String, Field> out = new HashMap<>();
          for (Class<?> c = t; c != null; c = c.getSuperclass())
            for (Field f : c.getDeclaredFields()) {
              if (Modifier.isStatic(f.getModifiers())) continue;
              try {
                f.setAccessible(true);
                out.putIfAbsent(f.getName(), f);
                for (Annotation a : f.getDeclaredAnnotations())
                  if (a.annotationType()
                      .getName()
                      .equals("com.google.gson.annotations.SerializedName")) {
                    Object name = a.annotationType().getMethod("value").invoke(a);
                    if (name instanceof String) out.putIfAbsent((String) name, f);
                  }
              } catch (Throwable ignored) {
              }
            }
          return out;
        });
  }

  static Object read(Object obj, String... names) {
    if (obj == null) return null;
    Map<String, Field> meta = fields(obj.getClass());
    for (String n : names) {
      Field f = meta.get(n);
      if (f != null)
        try {
          return f.get(obj);
        } catch (Throwable ignored) {
        }
      try {
        return call(obj, "get" + Character.toUpperCase(n.charAt(0)) + n.substring(1));
      } catch (Throwable ignored) {
      }
    }
    return null;
  }

  static void write(Object obj, Object value, String... names) throws Throwable {
    Map<String, Field> meta = fields(obj.getClass());
    for (String n : names) {
      Field f = meta.get(n);
      if (f != null) {
        f.set(obj, value);
        return;
      }
    }
    throw new NoSuchFieldException(Arrays.toString(names));
  }

  static Object call(Object obj, String name, Object... args) throws Throwable {
    if (obj == null) throw new NullPointerException(name);
    List<Method> list =
        methods.computeIfAbsent(
            obj.getClass(),
            t -> {
              List<Method> out = new ArrayList<>();
              for (Class<?> c = t; c != null; c = c.getSuperclass())
                for (Method m : c.getDeclaredMethods())
                  try {
                    m.setAccessible(true);
                    out.add(m);
                  } catch (Throwable ignored) {
                  }
              return out;
            });
    for (Method m : list)
      if (m.getName().equals(name) && m.getParameterCount() == args.length)
        try {
          return m.invoke(obj, args);
        } catch (IllegalArgumentException ignored) {
        } catch (InvocationTargetException e) {
          throw e.getCause();
        }
    throw new NoSuchMethodException(name);
  }

  static String text(Object o) {
    return o == null ? "" : String.valueOf(o);
  }

  static long number(Object o, long fallback) {
    return o instanceof Number ? ((Number) o).longValue() : fallback;
  }

  static boolean yes(Object o) {
    return Boolean.TRUE.equals(o) || o instanceof Number && ((Number) o).intValue() != 0;
  }

  static List<String> urls(Object model) {
    List<String> output = new ArrayList<>();
    if (model instanceof String) {
      String normalized = normalizeMediaUrl((String) model);
      if (!normalized.isEmpty()) output.add(normalized);
      return output;
    }
    Object list = read(model, "urlList", "url_list");
    if (list instanceof List<?>)
      for (Object entry : (List<?>) list)
        if (entry instanceof String) {
          String normalized = normalizeMediaUrl((String) entry);
          if (!normalized.isEmpty()) output.add(normalized);
        }
    return output;
  }

  private static String normalizeMediaUrl(String raw) {
    if (raw.startsWith("//")) raw = "https:" + raw;
    if (raw.startsWith("http://")) raw = "https://" + raw.substring(7);
    if (!raw.startsWith("https://")) return "";
    try {
      com.yolo.dyfusion.media.Downloads.allowed(new java.net.URL(raw));
      return raw;
    } catch (Throwable e) {
      return "";
    }
  }

  static String firstUrl(Object model) {
    List<String> list = urls(model);
    return list.isEmpty() ? "" : list.get(0);
  }
}
