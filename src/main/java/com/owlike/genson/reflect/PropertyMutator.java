package com.owlike.genson.reflect;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import com.owlike.genson.*;
import com.owlike.genson.stream.ObjectReader;

public abstract class PropertyMutator extends BeanProperty implements Comparable<PropertyMutator> {
	Deserializer<Object> propertyDeserializer;

	protected PropertyMutator(String name, Type type, Class<?> declaringClass, Annotation[] annotations, int modifiers) {
		super(name, type, declaringClass, annotations, modifiers);
	}

	public Object deserialize(ObjectReader reader, Context ctx) {
		try {
			return propertyDeserializer.deserialize(reader, ctx);
		} catch (Throwable th) {
			throw couldNotDeserialize(th);
		}
	}

	public void deserialize(Object into, ObjectReader reader, Context ctx) {
		Object propValue = null;
		try {
			propValue = propertyDeserializer.deserialize(reader, ctx);
		} catch (Throwable th) {
			throw couldNotDeserialize(th);
		}
		mutate(into, propValue);
	 }

	public abstract void mutate(Object target, Object value);

	public int compareTo(PropertyMutator o) {
		return o.priority() - priority();
	}
	
	protected JsonBindingException couldNotMutate(Exception e) {
		return new JsonBindingException("Could not mutate value of property named '"
				+ name + "' using mutator " + signature(), e);
	}
	
	protected JsonBindingException couldNotDeserialize(Throwable e) {
		return new JsonBindingException("Could not deserialize to property '" + name + "' of class " + declaringClass, e);
	}

	public static class MethodMutator extends PropertyMutator {
		protected final Method _setter;

		public MethodMutator(String name, Method setter, Type type, Class<?> declaringClass) {
			super(name, type, declaringClass, setter.getAnnotations(), setter.getModifiers());
			this._setter = setter;
			if (!_setter.isAccessible()) {
				_setter.setAccessible(true);
			}
		}

		@Override
		public void mutate(Object target, Object value) {
			try {
				_setter.invoke(target, value);
			} catch (IllegalArgumentException e) {
				throw couldNotMutate(e);
			} catch (IllegalAccessException e) {
				throw couldNotMutate(e);
			} catch (InvocationTargetException e) {
				throw couldNotMutate(e);
			}
		}

		@Override
		public String signature() {
			return _setter.toGenericString();
		}

		@Override
		public int priority() {
			return 100;
		}
	}

	public static class FieldMutator extends PropertyMutator {
		protected final Field _field;

		public FieldMutator(String name, Field field, Type type, Class<?> declaringClass) {
			super(name, type, declaringClass, field.getAnnotations(), field.getModifiers());
			this._field = field;
			if (!_field.isAccessible()) {
				_field.setAccessible(true);
			}
		}

		@Override
		public void mutate(Object target, Object value) {
			try {
				_field.set(target, value);
			} catch (IllegalArgumentException e) {
				throw couldNotMutate(e);
			} catch (IllegalAccessException e) {
				throw couldNotMutate(e);
			}
		}

		@Override
		public String signature() {
			return _field.toGenericString();
		}

		@Override
		public int priority() {
			return 0;
		}
	}
}
