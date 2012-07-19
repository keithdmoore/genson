package org.genson.convert;

import java.io.IOException;
import java.lang.reflect.Type;

import org.genson.ChainedFactory;
import org.genson.Context;
import org.genson.Genson;
import org.genson.TransformationException;
import org.genson.annotation.HandleNull;
import org.genson.stream.ObjectReader;
import org.genson.stream.ObjectWriter;
import org.genson.stream.ValueType;

/**
 * You can also change the way null values are handled by registering your own Converter
 * {@link org.genson.Genson.Builder#setNullConverter(org.genson.convert.Converter)
 * Genson.Builder.setNullConverter(org.genson.convert.Converter)}.
 * 
 * @author eugen
 * 
 */
public class NullConverter implements Converter<Object> {
	public static class NullConverterFactory extends ChainedFactory {
		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		protected Converter<?> create(Type type, Genson genson, Converter<?> nextConverter) {
			return Wrapper.toAnnotatedElement(nextConverter).isAnnotationPresent(HandleNull.class) ? nextConverter
					: new NullConverterWrapper(genson.getNullConverter(), nextConverter);
		}
	}

	public static class NullConverterWrapper<T> extends Wrapper<Converter<T>> implements
			Converter<T> {
		private final Converter<Object> nullConverter;
		private final Converter<T> converter;

		public NullConverterWrapper(Converter<Object> nullConverter, Converter<T> converter) {
			super(converter);
			this.nullConverter = nullConverter;
			this.converter = converter;
		}

		@Override
		public void serialize(T obj, ObjectWriter writer, Context ctx)
				throws TransformationException, IOException {
			if (obj == null) {
				nullConverter.serialize(obj, writer, ctx);
			} else {
				converter.serialize(obj, writer, ctx);
			}
		}

		@SuppressWarnings("unchecked")
		@Override
		public T deserialize(ObjectReader reader, Context ctx) throws TransformationException,
				IOException {
			if (ValueType.NULL.equals(reader.getValueType()))
				return (T) nullConverter.deserialize(reader, ctx);

			return converter.deserialize(reader, ctx);
		}
	}

	public NullConverter() {
	}

	@Override
	public void serialize(Object obj, ObjectWriter writer, Context ctx)
			throws TransformationException, IOException {
		writer.writeNull();
	}

	@Override
	public Object deserialize(ObjectReader reader, Context ctx) throws TransformationException,
			IOException {
		return null;
	}
}