package com.lvt4j.mangabank.dao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.lvt4j.mangabank.po.Book;
import com.lvt4j.mangabank.po.User;

import lombok.Getter;

/**
 *
 * @author LV on 2023年9月10日
 */
@Component
public class BookDao extends AbstractLuceneDao {

    @Getter
    @Value("${lucene.book.dir}")
    private String luceneFolder;

    @Getter
    @Value("${lucene.book.merge.min:128}")
    private double luceneMergeMin;
    @Getter
    @Value("${lucene.book.merge.max:2048}")
    private double luceneMergeMax;
    
    @Autowired@Lazy
    private TagDao tagDao;
    @Autowired@Lazy
    private UserDao userDao;
    
    public Book get(String path) throws IOException {
        readerLock.readLock().lock();
        try{
            TopDocs topDocs = searcher.search(Book.Query.builder().path(path).build().toLuceneQuery(reader, analyzer), 1);
            if(topDocs.totalHits.value==0) return null;
            return Book.fromDoc(searcher.doc(topDocs.scoreDocs[0].doc));
        }finally{
            readerLock.readLock().unlock();
        }
    }
    
    public void set(Book book) throws IOException {
        readerLock.writeLock().lock();
        try{
            writer.updateDocument(new Term("path", book.path), book.toDoc());
            writer.commit();
            
            lucene_refresh();
        }finally{
            readerLock.writeLock().unlock();
        }
    }
    
    
    
//    public void del(int id) throws IOException {
//        readerLock.writeLock().lock();
//        try{
//            writer.deleteDocuments(Book.Query.builder().id(id).build().toLuceneQuery(reader, analyzer));
//            writer.commit();
//            
//            lucene_refresh();
//        }finally{
//            readerLock.writeLock().unlock();
//        }
//    }
    
    public Pair<Long, List<Book>> search(Book.Query query, Sort sort, int pageNo, int pageSize) throws IOException {
        readerLock.readLock().lock();
        try{
            TopDocs topDocs = search(query.toLuceneQuery(reader, analyzer), pageNo*pageSize, sort);
            
            int fromIndex = (pageNo-1)*pageSize;
            if(fromIndex>=topDocs.totalHits.value) return Pair.of(0L, Collections.emptyList());
            
            int toIndex = Math.min(fromIndex + pageSize, (int)topDocs.totalHits.value);
            
            List<Book> books = new ArrayList<>(toIndex-fromIndex);
            for(; fromIndex<toIndex; fromIndex++){
                books.add(Book.fromDoc(searcher.doc(topDocs.scoreDocs[fromIndex].doc)));
            }
            
            return Pair.of(topDocs.totalHits.value, books);
        }finally{
            readerLock.readLock().unlock();
        }
    }

    public int count(Book.Query query) throws IOException{
        readerLock.readLock().lock();
        try{
            return searcher.count(query.toLuceneQuery(reader, analyzer));
        }finally{
            readerLock.readLock().unlock();
        }
    }
    
    public void setTitles(String path, LinkedHashSet<String> titles) throws IOException {
        readerLock.writeLock().lock();
        try{
            Book book = get(path);
            if(book==null) throw new IllegalArgumentException("本子不存在："+path);
            book.titles = titles;
            
            writer.updateDocument(new Term("path", book.path), book.toDoc());
            writer.commit();
            
            lucene_refresh();
        }finally {
            readerLock.writeLock().unlock();
        }
    }
    public void setTags(String path, LinkedHashSet<Integer> tagIds) throws IOException {
        readerLock.writeLock().lock();
        try{
            Book book = get(path);
            if(book==null) throw new IllegalArgumentException("本子不存在："+path);
            book.tags = CollectionUtils.isEmpty(tagIds)?null:tagIds;
            set(book);
            
            Set<Integer> allTagIds = new HashSet<>();
            if(book.tags!=null) allTagIds.addAll(book.tags);
            if(tagIds!=null) allTagIds.addAll(tagIds);
            tagDao.correctTagedCount(allTagIds);
        }finally {
            readerLock.writeLock().unlock();
        }
    }

    public void setCoverPath(String path, String coverPath) throws IOException{
        readerLock.writeLock().lock();
        try{
            Book book = get(path);
            if(book == null)
                throw new IllegalArgumentException("本子不存在：" + path);
            book.coverPath = coverPath;

            set(book);
        }finally{
            readerLock.writeLock().unlock();
        }
    }
    
    public void delete(String path) throws IOException {
        Book book;
        readerLock.writeLock().lock();
        try{
            book = get(path);
            if(book == null) return;
            
            writer.deleteDocuments(new Term("path", path));
            writer.commit();
            lucene_refresh();
        }finally{
            readerLock.writeLock().unlock();
        }
        userDao.onBookDelete(path);
        tagDao.correctTagedCount(book.tags);
    }
    
    public void onTagDelete(int tagId) throws IOException {
        readerLock.writeLock().lock();
        try{
            TopDocs topDocs = searcher.search(Book.Query.builder().tags(Collections.singleton(tagId)).build().toLuceneQuery(reader, analyzer), Integer.MAX_VALUE);
            if(topDocs.totalHits.value==0) return;
            
            for(int i = 0; i < topDocs.totalHits.value; i++){
                Book book = Book.fromDoc(searcher.doc(topDocs.scoreDocs[i].doc));
                if(book.tags == null || !book.tags.contains(tagId)) continue;

                book.tags.remove(tagId);
                writer.updateDocument(new Term("path", book.path), book.toDoc());
            }
            writer.commit();
            lucene_refresh();
        }finally{
            readerLock.writeLock().unlock();
        }
    }
    
    /**
     * 检查指定本子的favor,如果错误则纠正
     * @param paths
     * @throws IOException
     */
    public void correctFavor(Set<String> paths) throws IOException {
        if(CollectionUtils.isEmpty(paths)) return;
        
        readerLock.writeLock().lock();
        try{
            for(String path: paths){
                Book book = get(path);
                if(book == null) continue;
                
                int favor = userDao.count(User.Query.builder().favorBookPaths(Collections.singleton(path)).build());
                if(book.favor == favor) continue;
                
                book.favor = favor;
                writer.updateDocument(new Term("path", book.path), book.toDoc());
            }
            writer.commit();
            lucene_refresh();
        }finally{
            readerLock.writeLock().unlock();
        }
    }
    
}