package org.greports.utils;

import org.greports.annotations.Converter;
import org.greports.converters.AbstractValueConverter;
import org.greports.exceptions.ReportEngineReflectionException;

import java.util.List;

/**
 * Converter utils class. This one is for internal use of greports engine.
 */
public class ConverterUtils {

    private ConverterUtils() {}

    public static Object convertValue(final Object value, final Converter converter) throws ReportEngineReflectionException {
        final Class<? extends AbstractValueConverter> clazz = converter.converterClass();
        final AbstractValueConverter valueConverter = ReflectionUtils.newInstance(clazz);
        return valueConverter.convert(value, converter.params());
    }

    public static Object convertValue(final Object value, final List<Converter> converters, final Class<?> toClazz) throws ReportEngineReflectionException {
        for (final Converter converter : converters) {
            final Class<? extends AbstractValueConverter> clazz = converter.converterClass();
            final AbstractValueConverter valueConverter = ReflectionUtils.newInstance(clazz);
            if(valueConverter.getToClass().equals(toClazz)){
                return valueConverter.convert(value, converter.params());
            }
        }
        return value;
    }
}
