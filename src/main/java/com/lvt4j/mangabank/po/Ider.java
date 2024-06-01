package com.lvt4j.mangabank.po;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;

import com.lvt4j.mangabank.MangaBankAPP;
import com.lvt4j.mangabank.LuceneUtils;

import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@Data
public class Ider{

    public String type;
    public int id;
    
    @SneakyThrows
    public Document toDoc(){
        Document doc = new Document();
        doc.add(new StoredField("_all", MangaBankAPP.ObjectMapper.writeValueAsBytes(this)));

        LuceneUtils.keywordField(doc, false, false, "type", type);
        LuceneUtils.intField(doc, false, false, false, "id", id);

        return doc;
    }
    
    @SneakyThrows
    public static Ider fromDoc(Document doc){
        return MangaBankAPP.ObjectMapper.readValue(doc.getBinaryValue("_all").bytes, Ider.class);
    }
    
    @Data@Builder
    @NoArgsConstructor@AllArgsConstructor
    public static class Query{
        
        public String type;

        @SneakyThrows
        public BooleanQuery toLuceneQuery(){
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            if(type != null) builder.add(new TermQuery(new Term("type", type)), Occur.MUST);
            return builder.build();
        }
    }
}