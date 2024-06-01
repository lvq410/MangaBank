package com.lvt4j.mangabank.learn;

import static com.lvt4j.mangabank.LuceneUtils.phraseQuery;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.LogByteSizeMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.lvt4j.mangabank.LuceneUtils;

/**
 *
 * @author LV on 2023年9月28日
 */
public class LuceneLearn {

    private File folder = new File("lucene");
    
    private Path path = Paths.get("lucene");
    private StandardAnalyzer analyzer = new StandardAnalyzer();
    
    private Directory directory;
    
    private IndexWriter writer;
    private DirectoryReader reader;
    
    
    @Before
    public void before() throws Throwable {
        FileUtils.deleteQuietly(folder);
        
        directory = FSDirectory.open(path);
        
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        LogByteSizeMergePolicy mergePolicy = new LogByteSizeMergePolicy();
        mergePolicy.setMaxMergeMB(2*1024);
        mergePolicy.setMinMergeMB(128);
        config.setMergePolicy(mergePolicy);
        
        writer = new IndexWriter(directory, new IndexWriterConfig(analyzer));
    }
    
    @After
    public void after() {
        if(writer!=null){
            try{
                writer.close();
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        if(reader!=null){
            try{
                reader.close();
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    
    @Test
    public void 短语搜索() throws Throwable {
        Document doc1 = new Document();
        doc1.add(new TextField("title", "测试标题1", Store.YES));
        writer.addDocument(doc1);
        writer.commit();
        
        reader = DirectoryReader.open(writer);
        IndexSearcher searcher = new IndexSearcher(reader);
        
        Query query = LuceneUtils.phraseQuery(reader, analyzer, "title", "测试");
        
        TopDocs hits = searcher.search(query, 10);
        assertEquals(1, hits.totalHits.value, 0);
        
        query = LuceneUtils.phraseQuery(reader, analyzer, "title", "试测");
        hits = searcher.search(query, 10);
        assertEquals(0, hits.totalHits.value, 0);
    }
    @Test
    public void 多值短语搜索() throws Throwable {
        Document doc1 = new Document();
        LuceneUtils.textsField(doc1, true, "title", Arrays.asList("多值短","语搜索"));
        writer.addDocument(doc1);
        writer.commit();
        
        reader = DirectoryReader.open(writer);
        IndexSearcher searcher = new IndexSearcher(reader);
        
        Query query = LuceneUtils.phraseQuery(reader, analyzer, "title", "多值");
        
        TopDocs hits = searcher.search(query, 10);
        assertEquals(1, hits.totalHits.value, 0);
        
        query = LuceneUtils.phraseQuery(reader, analyzer, "title", "语搜");
        hits = searcher.search(query, 10);
        assertEquals(1, hits.totalHits.value, 0);
        
        query = LuceneUtils.phraseQuery(reader, analyzer, "title", "短语");
        hits = searcher.search(query, 10);
        assertEquals(0, hits.totalHits.value, 0);
    }
    
    @Test
    public void 多次变更时Reader不能复用() throws Throwable {
        Document doc1 = new Document();
        doc1.add(new TextField("title", "测试标题1", Store.YES));
        writer.addDocument(doc1);
        writer.commit();
        
        reader = DirectoryReader.open(writer);
        IndexSearcher searcher = new IndexSearcher(reader);
        
        Query query = LuceneUtils.phraseQuery(reader, analyzer, "title", "1");
        
        TopDocs hits = searcher.search(query, 10);
        assertEquals(1, hits.totalHits.value, 0);
        
        Document doc2 = new Document();
        doc2.add(new TextField("title", "测试标题2", Store.YES));
        writer.addDocument(doc2);
        writer.commit();
        
        assertFalse(reader.isCurrent()); //该方法能判断文档库是否已被更新了
        
        query = LuceneUtils.phraseQuery(reader, analyzer, "title", "2");
        hits = searcher.search(query, 10);
        assertEquals(0, hits.totalHits.value, 0);
        
        DirectoryReader oldReader = reader;
        reader = DirectoryReader.openIfChanged(reader); //文档库变更后，需要通过该方法获取新的reader对象
        assertNotNull(reader);
        oldReader.close(); //老的reader需要人肉close
        searcher = new IndexSearcher(reader); //并且需要用新的reader对象创建新的Searcher
        
        query = phraseQuery(reader, analyzer, "title", "2");
        hits = searcher.search(query, 10);
        assertEquals(1, hits.totalHits.value, 0);
    }
    
    @Test
    public void 删除文档() throws Throwable {
        Document doc1 = new Document();
        doc1.add(new StringField("id", "1", Store.NO));
        doc1.add(new TextField("title", "测试标题1", Store.NO));
        writer.addDocument(doc1);
        writer.commit();
        
        reader = DirectoryReader.open(writer);
        IndexSearcher searcher = new IndexSearcher(reader);
        
        Query query = phraseQuery(reader, analyzer, "title", "1");
        
        TopDocs hits = searcher.search(query, 10);
        assertEquals(1, hits.totalHits.value, 0);
        
        writer.deleteDocuments(new Term("id", "1"));
        writer.commit();
        
        DirectoryReader oldReader = reader;
        reader = DirectoryReader.openIfChanged(reader);
        assertNotNull(reader);
        oldReader.close();
        searcher = new IndexSearcher(reader);
        
        hits = searcher.search(query, 10);
        assertEquals(0, hits.totalHits.value, 0);
    }
    
    @Test
    public void 修改文档() throws Throwable {
        Document doc1 = new Document();
        doc1.add(new StringField("id", "1", Store.NO));
        doc1.add(new TextField("title", "测试标题1", Store.NO));
        writer.addDocument(doc1);
        writer.commit();
        
        reader = DirectoryReader.open(writer);
        IndexSearcher searcher = new IndexSearcher(reader);
        
        Query query = phraseQuery(reader, analyzer, "title", "1");
        
        TopDocs hits = searcher.search(query, 10);
        assertEquals(1, hits.totalHits.value, 0);
        
        doc1.removeField("title");
        doc1.add(new TextField("title", "测试标题2", Store.NO));
        writer.updateDocument(new Term("id", "1"), doc1);
        writer.commit();
        
        DirectoryReader oldReader = reader;
        reader = DirectoryReader.openIfChanged(reader);
        assertNotNull(reader);
        oldReader.close();
        searcher = new IndexSearcher(reader);
        
        hits = searcher.search(query, 10);
        assertEquals(0, hits.totalHits.value, 0);
        
        query = phraseQuery(reader, analyzer, "title", "2");
        hits = searcher.search(query, 10);
        assertEquals(1, hits.totalHits.value, 0);
        
//        该方法只支持byte或num类字段
//        writer.updateDocValues(new Term("id", "1"), new TextField("title", "测试标题3", Store.NO));
    }
    
    @Test
    public void 数字类字段() throws Throwable {
        Document doc1 = new Document();
        LuceneUtils.intField(doc1, true, true, true, "grade", 1);
        doc1.add(new IntPoint("grade", 1));
        writer.addDocument(doc1);
        writer.commit();
        
        reader = DirectoryReader.open(writer);
        IndexSearcher searcher = new IndexSearcher(reader);
        
        Query query = IntPoint.newExactQuery("grade", 1);
        TopDocs hits = searcher.search(query, 10);
        assertEquals(1, hits.totalHits.value, 0);
        
        query = IntPoint.newRangeQuery("grade", 0, 2);
        hits = searcher.search(query, 10);
        assertEquals(1, hits.totalHits.value, 0);
        
        query = IntPoint.newRangeQuery("grade", 2, 3);
        hits = searcher.search(query, 10);
        assertEquals(0, hits.totalHits.value, 0);
        
        query = IntPoint.newSetQuery("grade", 1,2,3);
        hits = searcher.search(query, 10);
        assertEquals(1, hits.totalHits.value, 0);
        
        query = IntPoint.newSetQuery("grade", 2,3,4);
        hits = searcher.search(query, 10);
        assertEquals(0, hits.totalHits.value, 0);
    }
    @Test
    public void 数字类字段_最大最小() throws Throwable {
        Document doc1 = new Document();
        LuceneUtils.intField(doc1, true, true, true, "grade", 1);
        writer.addDocument(doc1);
        
        Document doc2 = new Document();
        LuceneUtils.intField(doc2, true, true, true, "grade", 2);
        writer.addDocument(doc2);
        
        writer.commit();
        
        reader = DirectoryReader.open(writer);
        IndexSearcher searcher = new IndexSearcher(reader);
        
        SortField sortField = new SortField("grade", SortField.Type.INT, false);
        Sort sort = new Sort(sortField);
        Query query = IntPoint.newRangeQuery("grade", Integer.MIN_VALUE, Integer.MAX_VALUE);
        TopDocs hits = searcher.search(query, 1, sort);
        assertEquals(2, hits.totalHits.value, 0);
        Number min = reader.document(hits.scoreDocs[0].doc).getField("grade").numericValue();
        assertEquals(1, min.intValue());
        
        sortField = new SortField("grade", SortField.Type.INT, true);
        sort = new Sort(sortField);
        query = IntPoint.newRangeQuery("grade", Integer.MIN_VALUE, Integer.MAX_VALUE);
        hits = searcher.search(query, 1, sort);
        assertEquals(2, hits.totalHits.value, 0);
        Number max = reader.document(hits.scoreDocs[0].doc).getField("grade").numericValue();
        assertEquals(2, max.intValue());
    }
    
    @Test
    public void 多值数字类字段() throws Throwable {
        Document doc1 = new Document();
        LuceneUtils.intsField(doc1, true, true, "grade", Arrays.asList(1,2,3));
        writer.addDocument(doc1);
        
        Document doc2 = new Document();
        LuceneUtils.intsField(doc2, true, true, "grade", Arrays.asList(4,5,6));
        writer.addDocument(doc2);
        
        writer.commit();
        
        reader = DirectoryReader.open(writer);
        IndexSearcher searcher = new IndexSearcher(reader);
        
        Query query = IntPoint.newExactQuery("grade", 1);
        TopDocs hits = searcher.search(query, 1);
        assertEquals(1, hits.totalHits.value);
        IndexableField[] fields = searcher.doc(hits.scoreDocs[0].doc).getFields("grade");
        assertEquals(3, fields.length);
        assertEquals(Arrays.asList(1,2,3), Stream.of(fields).map(f->f.numericValue().intValue()).collect(Collectors.toList()));
        
        query = IntPoint.newExactQuery("grade", 2);
        hits = searcher.search(query, 1);
        assertEquals(1, hits.totalHits.value);
        fields = searcher.doc(hits.scoreDocs[0].doc).getFields("grade");
        assertEquals(3, fields.length);
        assertEquals(Arrays.asList(1,2,3), Stream.of(fields).map(f->f.numericValue().intValue()).collect(Collectors.toList()));
        
        query = IntPoint.newExactQuery("grade", 3);
        hits = searcher.search(query, 1);
        assertEquals(1, hits.totalHits.value);
        fields = searcher.doc(hits.scoreDocs[0].doc).getFields("grade");
        assertEquals(3, fields.length);
        assertEquals(Arrays.asList(1,2,3), Stream.of(fields).map(f->f.numericValue().intValue()).collect(Collectors.toList()));
        
        query = IntPoint.newExactQuery("grade", 4);
        hits = searcher.search(query, 1);
        assertEquals(1, hits.totalHits.value);
        fields = searcher.doc(hits.scoreDocs[0].doc).getFields("grade");
        assertEquals(3, fields.length);
        assertEquals(Arrays.asList(4,5,6), Stream.of(fields).map(f->f.numericValue().intValue()).collect(Collectors.toList()));
    }
    
    @Test
    public void keyword类() throws Throwable {
        Document doc1 = new Document();
        String uuid1 = UUID.randomUUID().toString();
        LuceneUtils.keywordField(doc1, true, true, "key_field", uuid1);
        writer.addDocument(doc1);
        
        Document doc2 = new Document();
        String uuid2 = UUID.randomUUID().toString();
        LuceneUtils.keywordField(doc2, true, true, "key_field", uuid2);
        writer.addDocument(doc2);
        
        writer.commit();
        
        reader = DirectoryReader.open(writer);
        IndexSearcher searcher = new IndexSearcher(reader);
        
        Query query = new TermQuery(new Term("key_field", uuid1));
        TopDocs hits = searcher.search(query, 1);
        assertEquals(1, hits.totalHits.value);
        assertEquals(uuid1, reader.document(hits.scoreDocs[0].doc).getField("key_field").stringValue());
        
        query = new TermQuery(new Term("key_field", uuid2));
        hits = searcher.search(query, 1);
        assertEquals(1, hits.totalHits.value);
        assertEquals(uuid2, reader.document(hits.scoreDocs[0].doc).getField("key_field").stringValue());
        
        //前缀匹配
        Query prefixQuery = new PrefixQuery(new Term("key_field", uuid1.substring(0, 4)));
        hits = searcher.search(prefixQuery, 1);
        assertEquals(1, hits.totalHits.value);
        assertEquals(uuid1, reader.document(hits.scoreDocs[0].doc).getField("key_field").stringValue());
        
        //排序
        Sort sort = new Sort(new SortField("key_field", SortField.Type.STRING, false));
        MatchAllDocsQuery matchAllDocsQuery = new MatchAllDocsQuery();
        hits = searcher.search(matchAllDocsQuery, 2, sort);
        assertEquals(2, hits.totalHits.value);
        assertEquals(uuid1.compareTo(uuid2)<0?uuid1:uuid2, reader.document(hits.scoreDocs[0].doc).getField("key_field").stringValue());
        assertEquals(uuid1.compareTo(uuid2)<0?uuid2:uuid1, reader.document(hits.scoreDocs[1].doc).getField("key_field").stringValue());
    }
    @Test
    public void keywords类() throws Throwable {
        String uuid = UUID.randomUUID().toString();
        
        Document doc1 = new Document();
        String uuid11 = UUID.randomUUID().toString(), uuid12 = UUID.randomUUID().toString();
        LuceneUtils.keywordsField(doc1, false, true, "key_field", Arrays.asList(uuid, uuid11, uuid12));
        writer.addDocument(doc1);
        
        Document doc2 = new Document();
        String uuid21 = UUID.randomUUID().toString(), uuid22 = UUID.randomUUID().toString();
        LuceneUtils.keywordsField(doc2, false, true, "key_field", Arrays.asList(uuid, uuid21, uuid22));
        writer.addDocument(doc2);
        
        writer.commit();
        
        reader = DirectoryReader.open(writer);
        IndexSearcher searcher = new IndexSearcher(reader);
        
        Query query = new TermQuery(new Term("key_field", uuid11));
        TopDocs hits = searcher.search(query, 1);
        assertEquals(1, hits.totalHits.value);
        IndexableField[] fields = searcher.doc(hits.scoreDocs[0].doc).getFields("key_field");
        assertEquals(Arrays.asList(uuid, uuid11, uuid12), Stream.of(fields).map(f->f.stringValue()).collect(Collectors.toList()));
        
        query = new TermQuery(new Term("key_field", uuid12));
        hits = searcher.search(query, 1);
        assertEquals(1, hits.totalHits.value);
        fields = searcher.doc(hits.scoreDocs[0].doc).getFields("key_field");
        assertEquals(Arrays.asList(uuid, uuid11, uuid12), Stream.of(fields).map(f->f.stringValue()).collect(Collectors.toList()));
        
        query = new TermQuery(new Term("key_field", uuid21));
        hits = searcher.search(query, 1);
        assertEquals(1, hits.totalHits.value);
        fields = searcher.doc(hits.scoreDocs[0].doc).getFields("key_field");
        assertEquals(Arrays.asList(uuid, uuid21, uuid22), Stream.of(fields).map(f->f.stringValue()).collect(Collectors.toList()));
        
        query = new TermQuery(new Term("key_field", uuid22));
        hits = searcher.search(query, 1);
        assertEquals(1, hits.totalHits.value);
        fields = searcher.doc(hits.scoreDocs[0].doc).getFields("key_field");
        assertEquals(Arrays.asList(uuid, uuid21, uuid22), Stream.of(fields).map(f->f.stringValue()).collect(Collectors.toList()));
        
        query = new TermQuery(new Term("key_field", uuid));
        hits = searcher.search(query, 1);
        assertEquals(2, hits.totalHits.value);
        
        query = new BooleanQuery.Builder()
            .add(new TermQuery(new Term("key_field", uuid)), Occur.MUST)
            .add(new TermQuery(new Term("key_field", uuid11)), Occur.MUST)
        .build();
        hits = searcher.search(query, 1);
        assertEquals(1, hits.totalHits.value);
        fields = searcher.doc(hits.scoreDocs[0].doc).getFields("key_field");
        assertEquals(Arrays.asList(uuid, uuid11, uuid12), Stream.of(fields).map(f->f.stringValue()).collect(Collectors.toList()));
    }
    
}