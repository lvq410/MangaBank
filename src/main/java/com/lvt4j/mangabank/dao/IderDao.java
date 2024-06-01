package com.lvt4j.mangabank.dao;

import java.io.IOException;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.lvt4j.mangabank.po.Ider;

import lombok.Getter;

@Component
public class IderDao extends AbstractLuceneDao{

    @Getter
    @Value("${lucene.id.dir}")
    private String luceneFolder;

    @Getter
    @Value("${lucene.tag.merge.min:128}")
    private double luceneMergeMin;
    @Getter
    @Value("${lucene.tag.merge.max:2048}")
    private double luceneMergeMax;

    public int genId(Class<?> poClass) throws IOException {
        String type = poClass.getSimpleName();
        readerLock.writeLock().lock();
        try{
            Query query = Ider.Query.builder().type(type).build().toLuceneQuery();
            TopDocs topDocs = searcher.search(query, 1);
            
            Ider ider;
            if(topDocs.totalHits.value==0) {
                ider = new Ider();
                ider.type = type;
                ider.id = 1;
            }else{
                ider = Ider.fromDoc(searcher.doc(topDocs.scoreDocs[0].doc));
                ider.id += 1;
            }
            
            writer.deleteDocuments(query);
            writer.addDocument(ider.toDoc());
            writer.commit();
            
            lucene_refresh();
            
            return ider.id;
        }finally{
            readerLock.writeLock().unlock();
        }
    }
    
}