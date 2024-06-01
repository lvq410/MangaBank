package com.lvt4j.mangabank;

import java.io.File;
import java.security.MessageDigest;

import javax.xml.bind.DatatypeConverter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.BeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.SneakyThrows;


@SuppressWarnings("deprecation")
@SpringBootApplication
public class MangaBankAPP {

    public static final File WebFolder = new File("web");
    
    public static final ObjectMapper ObjectMapper = new ObjectMapper();
    
    static{
        ObjectMapper.setSerializationInclusion(Include.NON_NULL);
        ObjectMapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        ObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ObjectMapper.configure(Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        ObjectMapper.configure(Feature.ALLOW_SINGLE_QUOTES, true);
        ObjectMapper.configure(Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        ObjectMapper.configure(Feature.ALLOW_COMMENTS, true);
        ObjectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        ObjectMapper.registerModule(new JavaTimeModule());
        ObjectMapper.setFilterProvider(new FilterProvider() {
            @Override public BeanPropertyFilter findFilter(Object filterId) {return null;}
            @Override
            public PropertyFilter findPropertyFilter(Object filterId,
                    Object valueToFilter) {
                return SimpleBeanPropertyFilter.serializeAll();
            }
        });
    }
    
    
    public static void main(String[] args) throws Exception {
        SpringApplication.run(MangaBankAPP.class, args);
    }
    
    @SneakyThrows
    public static String md5(String text) {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(text.getBytes());
        return DatatypeConverter.printHexBinary(md.digest());
    }
    

    
}