package com.lvt4j.mangabank;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;

@Configuration
public class SpringMVCConfig implements WebMvcConfigurer {

    @Getter
    @Value("${server.servlet.context-path:}")
    private String ctxPath;
    
    @Bean
    public ObjectMapper objectMapper() {
        return MangaBankAPP.ObjectMapper;
    }
    
    @Bean
    public MappingJackson2HttpMessageConverter jackson2HttpMessageConverter(
            @Autowired ObjectMapper objectMapper) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        converter.setSupportedMediaTypes(Arrays.asList(MediaType.APPLICATION_JSON_UTF8));
        return converter;
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**").addResourceLocations(MangaBankAPP.WebFolder.toURI().toString());
    }
    
}