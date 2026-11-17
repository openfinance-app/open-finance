package org.openfinance.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import java.time.LocalDateTime;
import java.util.List;
import org.openfinance.util.ServerTimestampSerializer;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Unambiguous HTTP timestamps, without changing calendar dates or internal archive formats. */
@Configuration
public class TimestampJsonConfig implements WebMvcConfigurer {
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        SimpleModule module = new SimpleModule("ApiTimestamps");
        module.addSerializer(LocalDateTime.class, new ServerTimestampSerializer());
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
                jackson.setObjectMapper(jackson.getObjectMapper().copy().registerModule(module));
            }
        }
    }
}
