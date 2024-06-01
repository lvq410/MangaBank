package com.lvt4j.mangabank.controller.admin;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.ImmutableMap;
import com.lvt4j.mangabank.dao.TagDao;
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
        Pair<Long, List<Tag>> pair = dao.search(query, Tag.Query.CreateTimeAsc, query.pageNo, query.pageSize);
        
        return ImmutableMap.of(
            "count", pair.getLeft(),
            "list", pair.getRight());
    }
    
    @Data
    @ToString(callSuper=true)@EqualsAndHashCode(callSuper=true)
    static class Query extends Tag.Query{
        public int pageNo = 1;
        public int pageSize = 10;
    }
    
    @PutMapping
    public boolean set(
            @RequestParam(required=false) Integer id,
            @RequestBody String tag) throws IOException{
        dao.set(id, tag);
        return true;
    }
    
    @DeleteMapping
    public boolean delete(
            @RequestParam int id) throws IOException{
        dao.del(id);
        return true;
    }
    
}