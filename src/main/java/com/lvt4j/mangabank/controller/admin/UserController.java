package com.lvt4j.mangabank.controller.admin;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.ImmutableMap;
import com.lvt4j.mangabank.dao.UserDao;
import com.lvt4j.mangabank.po.User;

/**
 *
 * @author LV on 2023年12月1日
 */
@RequestMapping("admin/user")
@RestController("admin/user")
class UserController {

    @Autowired
    private UserDao dao;
    
    @PostMapping("list")
    public Map<String, Object> list(
            @RequestParam int pageNo,
            @RequestParam int pageSize,
            @RequestBody User.Query query) throws IOException {
        Pair<Long, List<User>> pair = dao.search(query, User.Query.CreateTimeAsc, pageNo, pageSize);
        
        return ImmutableMap.of(
            "count", pair.getLeft(),
            "list", pair.getRight());
    }
    
    @PatchMapping("admin")
    public boolean admin(
            @RequestParam String id,
            @RequestParam boolean admin) throws IOException {
        dao.setAdmin(id, admin);
        return true;
    }
    
    @DeleteMapping
    public boolean delete(
            @RequestParam String id) throws IOException {
        int otherAdminNum = dao.count(User.Query.builder().idNot(id).admin(true).build());
        if(otherAdminNum==0) throw new RuntimeException("禁止删除：至少要保留一个管理员");
        
        dao.delete(id);
        return true;
    }
    
}