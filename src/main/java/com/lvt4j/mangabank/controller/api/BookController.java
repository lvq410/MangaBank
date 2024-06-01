package com.lvt4j.mangabank.controller.api;

import static java.util.Collections.emptyMap;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.server.ResponseStatusException;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.lvt4j.mangabank.dao.BookDao;
import com.lvt4j.mangabank.dao.TagDao;
import com.lvt4j.mangabank.dao.UserDao;
import com.lvt4j.mangabank.po.Book;
import com.lvt4j.mangabank.po.User;
import com.lvt4j.mangabank.service.FileService;

import lombok.Data;

/**
 *
 * @author LV on 2023年9月10日
 */
@RestController("book")
@RequestMapping("book")
class BookController {

    @Autowired
    private BookDao dao;
    @Autowired
    private TagDao tagDao;
    @Autowired
    private UserDao userDao;

    @Autowired
    private FileService fileService;
    
    @PostMapping(value="list")
    public Object list(
            @SessionAttribute("UserId") String userId,
            @RequestBody Query query) throws Exception {
        User user = userDao.get(userId);
        if(user==null) throw new ResponseStatusException(NOT_FOUND, "未登录");
        
        Book.Query bookQuery = new Book.Query();
        bookQuery.titlePhrase = query.q;
        bookQuery.tags = query.tags;
        bookQuery.root = true;
        if(query.onlyFavor) bookQuery.pathContains = user.favorBooks.keySet();
        
        Pair<Long, List<Book>> searched = dao.search(bookQuery, Book.Query.FavorDescUpdateDesc, query.pageNo, query.pageSize);
        
        List<BookVo> vos = new ArrayList<>(searched.getRight().size());
        for(Book book: searched.getRight()){
            BookVo vo = new BookVo();
            vo.path = book.path;
            vo.titles = book.titles;
            vo.coverPath = book.coverPath;
            vo.tags = tagDao.toTexts(book.tags);
            vo.favor = defaultIfNull(user.favorBooks, emptyMap()).containsKey(book.path);
            vos.add(vo);
        }
        
        return ImmutableMap.of(
            "total", searched.getLeft(),
            "list", vos);
    }
    @Data
    static class Query {
        public String q;
        public Set<Integer> tags;
        public boolean onlyFavor;
        public int pageNo;
        public int pageSize;
    }
    

    @GetMapping
    public BookVo get(@RequestParam String path) throws Exception {
        Book book = dao.get(path);
        if(book==null) return null;
        
        BookVo vo = new BookVo();
        vo.path = book.path;
        vo.titles = book.titles;
        vo.coverPath = book.coverPath;
        vo.tags = tagDao.toTexts(book.tags);
        vo.collection = book.collection;
        vo.imgPaths = fileService.extractImgPathsFromPath(book.path);
        if(vo.collection){
            Book.Query childQuery = Book.Query.builder().parentPath(book.path).build();
            List<Book> childBooks = dao.search(childQuery, Book.Query.SequenceInParentAsc, 1, Integer.MAX_VALUE).getRight();
            
            vo.children = new ArrayList<>(childBooks.size());
            for(Book child: childBooks){
                BookVo childVo = new BookVo();
                childVo.path = child.path;
                childVo.titles = child.titles;
                childVo.coverPath = child.coverPath;
                vo.children.add(childVo);
            }
        }
        
        return vo;
    }

    class BookVo {
        public String path;
        public Collection<String> titles;
        public String coverPath;
        public Collection<String> tags;

        /** 是否被当前用户收藏 */
        public boolean favor;
        
        /** 是否是个Collection */
        public boolean collection;
        
        /**
         * collection类型时子book
         */
        public List<BookVo> children;
        
        /**
         * 卷类型时图片
         */
        public List<String> imgPaths;
    }
    
    @PostMapping("favor")
    public boolean toggleFavor(
            @SessionAttribute("UserId") String userId,
            @RequestParam String path,
            @RequestParam boolean favor) throws Exception{
        if(userDao.toggleBookFavor(userId, path, favor)) {
            dao.correctFavor(ImmutableSet.of(path));
        }
        return true;
    }
    
}