package com.lvt4j.mangabank.dao;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.PostConstruct;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogByteSizeMergePolicy;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 *
 * @author LV on 2023年11月14日
 */
public abstract class AbstractLuceneDao {

    protected abstract String getLuceneFolder();
    protected abstract double getLuceneMergeMin();
    protected abstract double getLuceneMergeMax();
    
    protected Analyzer analyzer;
    
    protected IndexWriter writer;
    
    protected DirectoryReader reader;
    protected IndexSearcher searcher;
    
    protected ReentrantReadWriteLock readerLock = new ReentrantReadWriteLock();
    
    @PostConstruct
    private void init() throws IOException {
        File folder = new File(getLuceneFolder()); folder.mkdirs();
        
        Directory directory = FSDirectory.open(folder.toPath());
        
        analyzer = analyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        LogByteSizeMergePolicy mergePolicy = new LogByteSizeMergePolicy();
        mergePolicy.setMaxMergeMB(getLuceneMergeMax());
        mergePolicy.setMinMergeMB(getLuceneMergeMin());
        config.setMergePolicy(mergePolicy);
        
        writer = new IndexWriter(directory, new IndexWriterConfig(analyzer));
        
        reader = DirectoryReader.open(writer);
        searcher = new IndexSearcher(reader);
    }
    
    protected Analyzer analyzer(){
        return new StandardAnalyzer();
    }
    
    protected void lucene_refresh() throws IOException {
        readerLock.writeLock().lock();
        try{
            DirectoryReader newReader = DirectoryReader.openIfChanged(reader); //文档库变更后，需要通过该方法获取新的reader对象
            if(newReader==null) return; //为null时，说明没有变更
            reader.close(); //老的reader需要人肉close
            reader = newReader;
            searcher = new IndexSearcher(reader); //并且需要用新的reader对象创建新的Searcher
        }finally{
            readerLock.writeLock().unlock();
        }
    }
    
    /**
     * sort null safe search
     * @param query
     * @param n
     * @param sort
     * @return
     */
    protected TopDocs search(org.apache.lucene.search.Query query, int n, Sort sort) throws IOException {
        return sort==null?searcher.search(query, n):searcher.search(query, n, sort);
    }
    
}
