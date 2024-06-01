package com.lvt4j.mangabank.controller.admin;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.ImmutableMap;
import com.lvt4j.mangabank.dao.ImgCacheDao;
import com.lvt4j.mangabank.po.ImgCache;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@RestController("admin/imgCache")
@RequestMapping("admin/imgCache")
public class ImgCacheController{

    @Autowired
    private ImgCacheDao dao;
    
    @PostMapping("list")
    public Map<String, Object> list(
            @RequestBody Query query) throws Exception {
        Pair<Long, List<ImgCache>> searched = dao.search(query, ImgCache.Query.PathAscResolutionAsc, query.pageNo, query.pageSize);
        
        return ImmutableMap.of(
            "count", searched.getLeft(),
            "list", searched.getRight());
    }
    
    @Data
    @ToString(callSuper=true)@EqualsAndHashCode(callSuper=true)
    static class Query extends ImgCache.Query{
        public int pageNo = 1;
        public int pageSize = 10;
    }
    
    @PostMapping("clear")
    public boolean clear(
            @RequestBody Query query) throws Exception {
        dao.delete(query);
        return true;
    }
    
}