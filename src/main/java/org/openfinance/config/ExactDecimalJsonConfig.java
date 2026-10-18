package org.openfinance.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Carries editable decimals as text alongside the existing numeric API fields. JavaScript's JSON
 * parser rounds numeric tokens before forms can convert them to strings. Keeping an exact
 * representation avoids changing chart/report contracts and covers nested responses such as splits.
 * This module is HTTP-only: persistence, backups and operation-history serialization are unchanged.
 */
@Configuration
public class ExactDecimalJsonConfig implements WebMvcConfigurer {
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
                jackson.setObjectMapper(jackson.getObjectMapper().copy().registerModule(module()));
            }
        }
    }

    static SimpleModule module() {
        SimpleModule module = new SimpleModule("ExactDecimalResponses");
        module.setSerializerModifier(
                new BeanSerializerModifier() {
                    @Override
                    public List<BeanPropertyWriter> changeProperties(
                            SerializationConfig config,
                            BeanDescription bean,
                            List<BeanPropertyWriter> properties) {
                        String packageName = bean.getBeanClass().getPackageName();
                        if (!packageName.startsWith("org.openfinance.dto")
                                && !packageName.equals("org.openfinance.entity")) return properties;
                        List<BeanPropertyWriter> decimals =
                                properties.stream()
                                        .filter(
                                                property ->
                                                        property.getType()
                                                                .hasRawClass(BigDecimal.class))
                                        .toList();
                        if (!decimals.isEmpty()) properties.add(new ExactValuesWriter(decimals));
                        return properties;
                    }
                });
        return module;
    }

    private static final class ExactValuesWriter extends BeanPropertyWriter {
        private static final long serialVersionUID = 1L;
        private final List<BeanPropertyWriter> decimals;

        private ExactValuesWriter(List<BeanPropertyWriter> decimals) {
            super(decimals.getFirst(), new SerializedString("exactValues"));
            this.decimals = List.copyOf(decimals);
        }

        @Override
        public void serializeAsField(
                Object bean, JsonGenerator generator, SerializerProvider provider)
                throws Exception {
            generator.writeObjectFieldStart("exactValues");
            for (BeanPropertyWriter property : decimals) {
                BigDecimal value = (BigDecimal) property.get(bean);
                if (value != null)
                    generator.writeStringField(property.getName(), value.toPlainString());
            }
            generator.writeEndObject();
        }
    }
}
