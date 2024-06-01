package com.lvt4j.mangabank.dao;

import static java.util.stream.Collectors.toCollection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.lvt4j.mangabank.po.Book;
import com.lvt4j.mangabank.po.Tag;

import lombok.Getter;
import lombok.SneakyThrows;

/**
 *
 * @author LV on 2023年9月10日
 */
@Component
public class TagDao extends AbstractLuceneDao {
    
    @Getter
    @Value("${lucene.tag.dir}")
    private String luceneFolder;

    @Getter
    @Value("${lucene.tag.merge.min:128}")
    private double luceneMergeMin;
    @Getter
    @Value("${lucene.tag.merge.max:2048}")
    private double luceneMergeMax;
    
    @Autowired
    private IderDao iderDao;
    
    @Autowired@Lazy
    private BookDao bookDao;
    
    @Override
    protected Analyzer analyzer(){
        StandardAnalyzer analyzer = new StandardAnalyzer();
        analyzer.setMaxTokenLength(1);
        return analyzer;
    }
    
    private LoadingCache<Integer, Optional<Tag>> cache = CacheBuilder.newBuilder()
        .build(new CacheLoader<Integer, Optional<Tag>>(){
            @Override
            public Optional<Tag> load(Integer id) throws Exception{
                return Optional.ofNullable(get(id));
            }
    });
    
    public Tag get(Integer id) throws IOException {
        readerLock.readLock().lock();
        try{
            TopDocs topDocs = searcher.search(Tag.Query.builder().id(id).build().toLuceneQuery(reader, analyzer), 1);
            if(topDocs.totalHits.value==0) return null;
            return Tag.fromDoc(searcher.doc(topDocs.scoreDocs[0].doc));
        }finally{
            readerLock.readLock().unlock();
        }
    }
    
    @SneakyThrows
    public Tag getByCache(Integer id) {
        return cache.get(id).orElse(null);
    }
    
    public Collection<Tag> getsByCache(Set<Integer> tagIds){
        if(CollectionUtils.isEmpty(tagIds)) return null;
        return tagIds.stream().map(this::getByCache).filter(Objects::nonNull).collect(Collectors.toList());
    }
    
    public LinkedHashSet<String> toTexts(Collection<Integer> tagIds){
        if(CollectionUtils.isEmpty(tagIds)) return null;
        return tagIds.stream().map(this::getByCache).filter(Objects::nonNull).map(Tag::getTag).collect(toCollection(LinkedHashSet::new));
    }
    
    public Pair<Long, List<Tag>> search(Tag.Query query, Sort sort, int pageNo, int pageSize) throws IOException{
        readerLock.readLock().lock();
        try{
            TopDocs topDocs = search(query.toLuceneQuery(reader, analyzer), pageNo*pageSize, sort);
            
            int fromIndex = (pageNo-1)*pageSize;
            if(fromIndex>=topDocs.totalHits.value) return Pair.of(topDocs.totalHits.value, Collections.emptyList());
            
            int toIndex = Math.min(fromIndex+pageSize, (int)topDocs.totalHits.value);
            
            List<Tag> tags = new ArrayList<>(toIndex-fromIndex);
            for(; fromIndex<toIndex; fromIndex++){
                tags.add(Tag.fromDoc(searcher.doc(topDocs.scoreDocs[fromIndex].doc)));
            }
            
            return Pair.of(topDocs.totalHits.value, tags);
        }finally{
            readerLock.readLock().unlock();
        }
    }
    
    public void set(Integer id, String t) throws IOException {
        readerLock.writeLock().lock();
        try{
            Tag tag = new Tag();
            tag.tag = t;
            
            if(id==null){ //新增
                //检查tag重复
                TopDocs topDocs = searcher.search(Tag.Query.builder().tag(t).build().toLuceneQuery(reader,analyzer), 1);
                if(topDocs.totalHits.value>=1) throw new ResponseStatusException(HttpStatus.CONFLICT, "已有标签："+t);
                //生成id
                tag.id = iderDao.genId(Tag.class);
                tag.createTime = new Date();
            }else{ //修改
                //检查tag重复
                TopDocs topDocs = searcher.search(Tag.Query.builder().idNot(id).tag(t).build().toLuceneQuery(reader, analyzer), 1);
                if(topDocs.totalHits.value>=1) throw new ResponseStatusException(HttpStatus.CONFLICT, "已有标签："+t);
                topDocs = searcher.search(Tag.Query.builder().id(id).build().toLuceneQuery(reader, analyzer), 1);
                if(topDocs.totalHits.value==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "标签不存在："+id);
                //保留原数据的一些字段
                Tag orig = Tag.fromDoc(searcher.doc(topDocs.scoreDocs[0].doc));
                tag.id = id;
                tag.alikeIds = orig.alikeIds;
                tag.tagedCount = orig.tagedCount;
                tag.createTime = orig.createTime;
                //先删除原文档
                writer.deleteDocuments(Tag.Query.builder().id(tag.id).build().toLuceneQuery(reader, analyzer));
            }
            tag.updateTime = new Date();
            writer.addDocument(tag.toDoc());
            writer.commit();
            
            lucene_refresh();
            cache.invalidate(tag.id);
        }finally{
            readerLock.writeLock().unlock();
        }
    }
    
    public void del(int id) throws IOException {
        readerLock.writeLock().lock();
        try{
            writer.deleteDocuments(Tag.Query.builder().id(id).build().toLuceneQuery(reader, analyzer));
            writer.commit();
            
            lucene_refresh();
            cache.invalidate(id);
        }finally{
            readerLock.writeLock().unlock();
        }
        bookDao.onTagDelete(id);
    }
    
    /**
     * 根据一批标签文案，获取对应标签id，如果不存在，就创建标签
     * @param tags
     * @return
     */
    public LinkedHashSet<Integer> getOrGens(Set<String> tags) throws IOException {
        if(CollectionUtils.isEmpty(tags)) return null;
        
        readerLock.writeLock().lock();
        try{
            LinkedHashSet<Integer> tagIds = new LinkedHashSet<>();
            List<Integer> createdTagIds = new ArrayList<>();
            
            for(String tag: tags){
                TopDocs topDocs = searcher.search(Tag.Query.builder().tag(tag).build().toLuceneQuery(reader, analyzer), 1);
                if(topDocs.totalHits.value == 0){
                    Tag newTag = new Tag();
                    newTag.id = iderDao.genId(Tag.class);
                    newTag.tag = tag;
                    newTag.createTime = new Date();
                    newTag.updateTime = newTag.createTime;
                    
                    writer.addDocument(newTag.toDoc());
                    
                    tagIds.add(newTag.id);
                    createdTagIds.add(newTag.id);
                }else{
                    tagIds.add(Tag.fromDoc(searcher.doc(topDocs.scoreDocs[0].doc)).id);
                }
            }
            writer.commit();
            
            lucene_refresh();
            cache.invalidateAll(createdTagIds);
            return tagIds;
        }finally{
            readerLock.writeLock().unlock();
        }
    }
    
    /**
     * 检查指定标签的tagedCount,如果错误则纠正
     * @param tagIds
     */
    public void correctTagedCount(Set<Integer> tagIds) throws IOException {
        if(CollectionUtils.isEmpty(tagIds)) return;
        
        readerLock.writeLock().lock();
        try{
            for(Integer tagId: tagIds){
                Tag tag = get(tagId);
                if(tag == null) continue;
                
                int tagedCount = bookDao.count(Book.Query.builder().tags(Arrays.asList(tagId)).build());
                if(tag.tagedCount == tagedCount) continue;
                
                tag.tagedCount = tagedCount;
                writer.deleteDocuments(Tag.Query.builder().id(tag.id).build().toLuceneQuery(reader, analyzer));
                writer.addDocument(tag.toDoc());
            }
            writer.commit();
            
            lucene_refresh();
        }finally{
            readerLock.writeLock().unlock();
        }
    }
    
    
//    /**
//     * 根据给定标签，附加其类同标签
//     * @param tagIds
//     * @return
//     * @throws IOException
//     */
//    public Collection<Integer> alikeIds(Collection<Integer> tagIds) throws IOException {
//        if(CollectionUtils.isEmpty(tagIds)) return null;
//        Set<Integer> allTags = new HashSet<>();
//        allTags.addAll(tagIds);
//        
//        readerLock.readLock().lock();
//        try{
//            TopDocs topDocs = searcher.search(Tag.Query.builder().alikeIds(tagIds).build().toLuceneQuery(), Integer.MAX_VALUE);
//            if(topDocs.totalHits.value==0) return Collections.emptyList();
//            for(ScoreDoc scoreDoc : topDocs.scoreDocs){
//                allTags.add(Tag.fromDoc(searcher.doc(scoreDoc.doc)).id);
//            }
//            return allTags;
//        }finally{
//            readerLock.readLock().unlock();
//        }
//    }
//    
}