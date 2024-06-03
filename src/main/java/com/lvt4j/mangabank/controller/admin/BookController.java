package com.lvt4j.mangabank.controller.admin;

import static org.springframework.http.HttpStatus.NO_CONTENT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.search.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.ImmutableMap;
import com.lvt4j.mangabank.dao.BookDao;
import com.lvt4j.mangabank.dao.TagDao;
import com.lvt4j.mangabank.dto.Sorts;
import com.lvt4j.mangabank.po.Book;
import com.lvt4j.mangabank.po.Tag;
import com.lvt4j.mangabank.service.FileService;
import com.lvt4j.mangabank.service.SyncService;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 *
 * @author LV on 2023年11月15日
 */
@RestController("admin/book")
@RequestMapping("admin/book")
class BookController {

    @Autowired
    private BookDao dao;
    @Autowired
    private TagDao tagDao;
    
    @Autowired
    private SyncService syncService;
    @Autowired
    private FileService fileService;

    /**
     * 执行
     * @param rootPaths
     * @return
     */
    @PostMapping("sync")
    @ResponseStatus(NO_CONTENT)
    public void sync(
            @RequestBody Set<String> rootPaths) throws IOException {
        syncService.syncRootPaths(rootPaths);
    }
    
    @PostMapping("list")
    public Map<String, Object> list(
            @RequestBody Query query) throws IOException {
        Sort sort = query.sorts==null?Book.Query.PathAsc:query.sorts.toSort(Book.Query.SortFieldTypes);
        query.root = true;
        Pair<Long, List<Book>> searched = dao.search(query, sort, query.pageNo, query.pageSize);
        
        List<BookVo> vos = new ArrayList<>(searched.getRight().size());
        for(Book book: searched.getRight()){
            BookVo vo = new BookVo();
            vo.path = book.path;
            vo.titles = book.titles;
            vo.coverPath = book.coverPath;
            vo.tags = tagDao.getsByCache(book.tags);
            vo.favor = book.favor;
            vo.createTime = book.createTime;
            vo.updateTime = book.updateTime;
            vos.add(vo);
        }
        
        return ImmutableMap.of(
            "total", searched.getLeft(),
            "list", vos);
    }
    
    @Data
    @ToString(callSuper=true)@EqualsAndHashCode(callSuper=true)
    static class Query extends Book.Query{
        public int pageNo = 1;
        public int pageSize = 10;
        public Sorts sorts;
    }
    
    @GetMapping
    public BookVo get(
            @RequestParam String path) throws Exception {
        Book book = dao.get(path);
        if(book==null) return null;
        
        BookVo vo = new BookVo();
        vo.path = book.path;
        vo.titles = book.titles;
        vo.coverPath = book.coverPath;
        vo.tags = tagDao.getsByCache(book.tags);
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
                childVo.createTime = child.createTime;
                childVo.updateTime = child.updateTime;
                vo.children.add(childVo);
            }
        }
        
        return vo;
    }
    
    class BookVo {
        public String path;
        public Collection<String> titles;
        public String coverPath;
        public Collection<Tag> tags;

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
        
        /** 收藏数量 */
        public int favor;
        public Date createTime;
        public Date updateTime;
    }
    
    @PatchMapping("titles")
    @ResponseStatus(NO_CONTENT)
    public void setTitlesTags(
            @RequestParam String path,
            @RequestBody LinkedHashSet<String> titles) throws IOException {
        dao.setTitles(path, titles);
    }
    @PatchMapping("tags")
    @ResponseStatus(NO_CONTENT)
    public void setTags(
            @RequestParam String path,
            @RequestBody LinkedHashSet<Integer> tagIds) throws IOException{
        dao.setTags(path, tagIds);
    }
    @PatchMapping("coverPath")
    @ResponseStatus(NO_CONTENT)
    public void setCoverPath(
            @RequestParam String path,
            @RequestBody String coverPath) throws IOException{
        dao.setCoverPath(path, coverPath);
    }
    
    @PatchMapping("imgs")
    @ResponseStatus(NO_CONTENT)
    public void setImgs(
            @RequestParam String path,
            @RequestBody SetImgsParam param) throws IOException{
        fileService.setImgs(path, param.imgPaths, param.delImgPaths);
    }
    @Data
    static class SetImgsParam {
        public LinkedHashSet<String> imgPaths;
        public Set<String> delImgPaths;
    }
    
    
}