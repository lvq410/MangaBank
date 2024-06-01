package com.lvt4j.mangabank;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.util.BytesRef;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import lombok.SneakyThrows;

/**
 *
 * @author LV on 2023年11月7日
 */
public class LuceneUtils {

    private static LoadingCache<IndexReader, Set<String>> indexFieldsCache = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.DAYS)
        .build(new CacheLoader<IndexReader, Set<String>>() {
            @Override
            public Set<String> load(IndexReader reader) throws Exception {
                return indexFields(reader);
            }
        });
    
    /**
     * 填充int单值字段
     * @param doc
     * @param filter 是否要支持筛选
     * @param sort 是否要支持排序
     * @param store 是否要存储进而能读取
     * @param fieldName 字段名
     * @param value 值
     */
    public static void intField(Document doc,
            boolean filter, boolean sort, boolean store, 
            String fieldName, Integer value) {
        if(value==null) return;
        if(filter) doc.add(new IntPoint(fieldName, value)); //过滤
        if(sort) doc.add(new NumericDocValuesField(fieldName, value)); //排序
        if(store) doc.add(new StoredField(fieldName, value)); //读取
    }
    
    /**
     * 填充int多值字段<br>
     * ps多值字段不支持排序
     * @param doc
     * @param filter 是否要支持筛选
     * @param store 是否要存储进而能读取
     * @param fieldName 字段名
     * @param values 值
     */
    public static void intsField(Document doc,
            boolean filter, boolean store, 
            String fieldName, Collection<Integer> values) {
        if(CollectionUtils.isEmpty(values)) return;
        for(Integer value : values){
            if(value==null) continue;
            if(filter) doc.add(new IntPoint(fieldName, value)); //过滤
            if(store) doc.add(new StoredField(fieldName, value)); //读取
        }
    }
    
    /**
     * 填充long单值字段
     * @param doc
     * @param filter 是否要支持筛选
     * @param sort 是否要支持排序
     * @param store 是否要存储进而能读取
     * @param fieldName 字段名
     * @param value 值
     */
    public static void longField(Document doc,
            boolean filter, boolean sort, boolean store, 
            String fieldName, Long value) {
        if(value==null) return;
        if(filter) doc.add(new LongPoint(fieldName, value)); //过滤
        if(sort) doc.add(new NumericDocValuesField(fieldName, value)); //排序
        if(store) doc.add(new StoredField(fieldName, value)); //读取
    }
    
    /**
     * 填充int多值字段<br>
     * ps多值字段不支持排序
     * @param doc
     * @param filter 是否要支持筛选
     * @param store 是否要存储进而能读取
     * @param fieldName 字段名
     * @param values 值
     */
    public static void longsField(Document doc,
            boolean filter, boolean store, 
            String fieldName, Collection<Long> values) {
        if(CollectionUtils.isEmpty(values)) return;
        for(Long value : values){
            if(value==null) continue;
            if(filter) doc.add(new LongPoint(fieldName, value)); //过滤
            if(store) doc.add(new StoredField(fieldName, value)); //读取
        }
    }
    
    /**
     * 填充多值文本字段
     * @param doc
     * @param store 是否要存储进而能读取
     * @param fieldName 字段名
     * @param values 值
     */
    public static void textsField(Document doc, boolean store,
            String fieldName, Collection<String> values) {
        if(CollectionUtils.isEmpty(values)) return;
        int idx = 0;
        for(String value : values){
            doc.add(new TextField(fieldName+"["+ idx++ +"]", value, store?Store.YES:Store.NO));
        }
    }
    
    public static void keywordField(Document doc, boolean sort, boolean store,
            String fieldName, String value) {
        if(StringUtils.isEmpty(value)) return;
        if(sort) doc.add(new SortedDocValuesField(fieldName, new BytesRef(value)));
        doc.add(new StringField(fieldName, value, store?Store.YES:Store.NO));
    }
    
    public static void keywordsField(Document doc, boolean sort, boolean store,
            String fieldName, Collection<String> values) {
        if(CollectionUtils.isEmpty(values)) return;
        for(String value : values){
            if(sort) doc.add(new SortedDocValuesField(fieldName, new BytesRef(value)));
            doc.add(new StringField(fieldName, value, store?Store.YES:Store.NO));
        }
    }
    
    public static void bitField(Document doc, boolean store,
            String fieldName, Boolean value) {
        if(value==null) return;
        intField(doc, true, false, store, fieldName, value?1:0);
    }
    public static void dateField(Document doc,
            boolean filter, boolean sort, boolean store,
            String fieldName, Date value) {
        if(value==null) return;
        longField(doc, filter, sort, store, fieldName, value.getTime());
    }
    
    
    
    /**
     * 构造短语搜索
     * @see #phraseQuery(IndexReader, int, Analyzer, String, String)
     */
    public static Query phraseQuery(IndexReader reader, Analyzer analyzer, String field, String query) {
        return phraseQuery(reader, 0, analyzer, field, query);
    }
    /**
     * 
     * 构造短语搜索<br>
     * 自动根据给定字段，分析其是否是{@link #stringsField}创建的多值字段
     * 如果不是，则构造普通的{@link PhraseQuery}
     * 否则构造由多个{@link PhraseQuery}复合的{@link BooleanQuery}
     * @param reader
     * @param slop
     * @param analyzer
     * @param field
     * @param query
     */
    @SneakyThrows
    public static Query phraseQuery(IndexReader reader, int slop, Analyzer analyzer, String field, String query) {
        String[] terms = terms(query, analyzer);
        Set<String> arrayFieldNames = arrayFieldNames(indexFieldsCache.get(reader), field);
        if(CollectionUtils.isEmpty(arrayFieldNames)) return new PhraseQuery(slop, field, terms);
        
        BooleanQuery.Builder bq = new BooleanQuery.Builder();
        for(String arrayFieldName : arrayFieldNames){
            bq.add(new PhraseQuery(slop, arrayFieldName, terms), Occur.SHOULD);
        }
        bq.setMinimumNumberShouldMatch(1);
        return bq.build();
    }
    
    /**
     * 文本按照给定分词器提取为词数组
     * @param text
     * @param analyzer
     * @return
     */
    @SneakyThrows
    public static String[] terms(String text, Analyzer analyzer) {
        List<String> terms = new ArrayList<>();
        TokenStream tokenStream = analyzer.tokenStream("field", text);
        CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
        tokenStream.reset();

        while(tokenStream.incrementToken()) {
            terms.add(charTermAttribute.toString());
        }
        tokenStream.end();
        tokenStream.close();
        return terms.toArray(new String[terms.size()]);
    }
    
    /**
     * 获取索引的所有字段
     * @param reader
     * @return
     */
    public static Set<String> indexFields(IndexReader reader) {
        Set<String> fields = new TreeSet<>();
        reader.leaves().forEach(ctx->ctx.reader().getFieldInfos().forEach(i->fields.add(i.name)));
        return fields;
    }
    
    /**
     * 根据索引的所有字段名，分析其符合{@link #stringsField}所创建的，字段arrayfieldName的所有真实字段名
     * @param indexFields 索引的所有字段名
     * @param arrayfieldName 可能是数组的字段
     * @return
     */
    public static Set<String> arrayFieldNames(Set<String> indexFields, String arrayfieldName) {
        String regex = "^"+arrayfieldName +"\\[[0-9]+\\]$";
        return indexFields.stream().filter(f->f.matches(regex)).collect(Collectors.toSet());
    }
}