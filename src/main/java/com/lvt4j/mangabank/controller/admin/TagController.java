package com.lvt4j.mangabank.controller.admin;

import static org.springframework.http.HttpStatus.NO_CONTENT;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.search.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.ImmutableMap;
import com.lvt4j.mangabank.dao.TagDao;
import com.lvt4j.mangabank.dto.Sorts;
import com.lvt4j.mangabank.po.Tag;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@RequestMapping("admin/tag")
@RestController("admin/tag")
class TagController{

    @Autowired
    private TagDao dao;
    
    @PostMapping("list")
    public Map<String, Object> list(
            @RequestBody Query query) throws IOException {
        Sort sort = query.sorts==null?Tag.Query.CreateTimeAsc:query.sorts.toSort(Tag.Query.SortFieldTypes);
        Pair<Long, List<Tag>> pair = dao.search(query, sort, query.pageNo, query.pageSize);
        
        return ImmutableMap.of(
            "count", pair.getLeft(),
            "list", pair.getRight());
    }
    
    @Data
    @ToString(callSuper=true)@EqualsAndHashCode(callSuper=true)
    static class Query extends Tag.Query{
        public int pageNo = 1;
        public int pageSize = 10;
        public Sorts sorts;
    }
    
    @PutMapping
    @ResponseStatus(NO_CONTENT)
    public void set(
            @RequestParam(required=false) Integer id,
            @RequestBody String tag) throws IOException{
        dao.set(id, tag);
    }
    
    @DeleteMapping
    @ResponseStatus(NO_CONTENT)
    public void delete(
            @RequestParam int id) throws IOException{
        dao.del(id);
    }
    
}