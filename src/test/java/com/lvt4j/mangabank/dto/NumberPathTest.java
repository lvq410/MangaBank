package com.lvt4j.mangabank.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

public class NumberPathTest{

    @Test
    public void nodeParseNumersTest(){
        String name = "vol 2-1.zip";
        BigDecimal[] numbers = NumberPath.Node.parseNumbers(name);
        assert numbers.length==2;
        assert numbers[0].intValue()==2;
        assert numbers[1].intValue()==1;
        
        name = "vol 2.zip";
        numbers = NumberPath.Node.parseNumbers(name);
        assert numbers.length==1;
        assert numbers[0].intValue()==2;
        
        name = "vol.zip";
        numbers = NumberPath.Node.parseNumbers(name);
        assert numbers==null;
    }
    
    @Test
    public void intsComparatorTest(){
        BigDecimal[] a = new BigDecimal[]{BigDecimal.valueOf(2),BigDecimal.valueOf(1)};
        BigDecimal[] b = new BigDecimal[]{BigDecimal.valueOf(2),BigDecimal.valueOf(2)};
        int compare = NumberPath.NumbersComparator.compare(a, b);
        assert compare<0;
        
        BigDecimal[] c = new BigDecimal[]{BigDecimal.valueOf(3)};
        compare = NumberPath.NumbersComparator.compare(c, a);
        assert compare>0;
        
        BigDecimal[] d = new BigDecimal[]{BigDecimal.valueOf(3),BigDecimal.valueOf(1)};
        compare = NumberPath.NumbersComparator.compare(d, c);
        assert compare>0;
    }
    
    @Test
    public void pathTest() {
        String a = "a/vol 1.zip";
        String b = "a/vol 2-2.zip";
        String c = "a/vol 2-1.zip";
        String d = "a/vol 1-1.zip";
        String e = "b/vol 1.zip";
        String f = "a";
        
        List<NumberPath> paths = Stream.of(a,b,c,d,e, f).map(NumberPath::new).sorted().collect(Collectors.toList());
        
        assert paths.get(0).path.equals(f);
        assert paths.get(1).path.equals(a);
        assert paths.get(2).path.equals(d);
        assert paths.get(3).path.equals(c);
        assert paths.get(4).path.equals(b);
        assert paths.get(5).path.equals(e);
    }
}