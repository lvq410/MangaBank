package com.lvt4j.mangabank.dto;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.LinkedList;
import java.util.Map;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;

import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.springframework.web.server.ResponseStatusException;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class Sorts extends LinkedList<Sorts.Item> {
    private static final long serialVersionUID = 8243408398743722346L;

    public Sort toSort(Map<String, SortField.Type> fieldTypes){
        SortField[] sortFields = stream().map(sf->sf.toLucene(fieldTypes)).toArray(SortField[]::new);
        return new Sort(sortFields);
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        
        @NotEmpty(message="排序属性名不能为空")
        @Pattern(regexp="[`0-9a-zA-Z_'\"]+",message="排序属性名异常")
        public String field;
        public boolean ascOrDesc;

        public SortField toLucene(Map<String, SortField.Type> fieldTypes){
            SortField.Type type = fieldTypes.get(this.field);
            if(type==null) throw new ResponseStatusException(BAD_REQUEST, "不存在的排序属性："+this.field);
            return new SortField(this.field, type, !ascOrDesc);
        }
        
    }
    
}