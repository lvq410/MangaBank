package com.lvt4j.mangabank.controller.api;

import java.io.IOException;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.ImmutableMap;
import com.lvt4j.mangabank.dao.TagDao;
import com.lvt4j.mangabank.po.Tag;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 *
 * @author LV on 2023年9月10日
 */
@RestController("apiTagController")
@RequestMapping("tag")
class TagController {

    @Autowired
    private TagDao dao;
    
    @PostMapping("list")
    public Object list(
            @RequestBody Query q) throws IOException {
        Pair<Long, List<Tag>> all = dao.search(q, Tag.Query.TagedCountDescUpdateTimeDesc, q.pageNo, q.pageSize);
        
        return ImmutableMap.of("total", all.getLeft(), "list", all.getRight());
    }
    
    @Data
    @ToString(callSuper=true)@EqualsAndHashCode(callSuper=true)
    static class Query extends Tag.Query{
        public int pageNo = 1;
        public int pageSize = 10;
    }
    
}