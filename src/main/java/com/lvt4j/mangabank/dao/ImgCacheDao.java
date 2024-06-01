package com.lvt4j.mangabank.dao;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableSet;
import com.lvt4j.mangabank.Resolution;
import com.lvt4j.mangabank.po.ImgCache;

import lombok.Getter;

@Component
public class ImgCacheDao extends AbstractLuceneDao{

    @Getter
    @Value("${lucene.imgCache.dir}")
    private String luceneFolder;

    @Getter
    @Value("${lucene.imgCache.merge.min:128}")
    private double luceneMergeMin;
    @Getter
    @Value("${lucene.imgCache.merge.max:2048}")
    private double luceneMergeMax;
    
    public boolean exist(String path, Resolution resolution) throws IOException{
        readerLock.readLock().lock();
        try{
            TopDocs topDocs = searcher.search(ImgCache.Query.builder().path(path).resolution(resolution).build().toLuceneQuery(), 1);
            return topDocs.totalHits.value > 0;
        }finally{
            readerLock.readLock().unlock();
        }
    }
    
    public ImgCache get(String path, Resolution resolution) throws IOException{
        readerLock.readLock().lock();
        try{
            TopDocs topDocs = searcher.search(ImgCache.Query.builder().path(path).resolution(resolution).build().toLuceneQuery(), 1);
            if(topDocs.totalHits.value==0) return null;
            return ImgCache.fromDoc(searcher.doc(topDocs.scoreDocs[0].doc));
        }finally{
            readerLock.readLock().unlock();
        }
    }
    
    public void set(ImgCache imgCache) throws IOException{
        readerLock.writeLock().lock();
        try{
            writer.deleteDocuments(ImgCache.Query.builder().path(imgCache.path).resolution(imgCache.resolution).build().toLuceneQuery());
            writer.addDocument(imgCache.toDoc());
            writer.commit();
            
            lucene_refresh();
        }finally{
            readerLock.writeLock().unlock();
        }
    }
    
    public void delete(ImgCache.Query query) throws IOException {
        readerLock.writeLock().lock();
        try{
            writer.deleteDocuments(query.toLuceneQuery());
            writer.commit();
            
            lucene_refresh();
        }finally{
            readerLock.writeLock().unlock();
        }
    }
    
    public Pair<Long, List<ImgCache>> search(ImgCache.Query query, Sort sort, int pageNo, int pageSize) throws IOException{
        readerLock.readLock().lock();
        try{
            TopDocs topDocs = search(query.toLuceneQuery(), pageNo*pageSize, sort);
            
            int fromIndex = (pageNo-1)*pageSize;
            if(fromIndex>=topDocs.totalHits.value) return Pair.of(topDocs.totalHits.value, Collections.emptyList());
            
            int toIndex = Math.min(fromIndex+pageSize, (int)topDocs.totalHits.value);
            
            List<ImgCache> imgCaches = new java.util.ArrayList<>(toIndex-fromIndex);
            for(; fromIndex<toIndex; fromIndex++){
                imgCaches.add(ImgCache.fromDoc(searcher.doc(topDocs.scoreDocs[fromIndex].doc, ImmutableSet.of("_all"))));
            }
            
            return Pair.of(topDocs.totalHits.value, imgCaches);
        }finally{
            readerLock.readLock().unlock();
        }
    }
    
}