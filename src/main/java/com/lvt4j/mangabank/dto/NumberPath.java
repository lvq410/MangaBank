package com.lvt4j.mangabank.dto;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 文件路径每层解析为数字
 *
 * @author chanceylee on 2024年5月22日
 */
@RequiredArgsConstructor
public class NumberPath implements Comparable<NumberPath>{

    public final String path;
    
    /**
     * 解析后的路径信息
     */
    public final List<Node> nodes;
    
    public NumberPath(String path){
        super();
        path = FilenameUtils.normalizeNoEndSeparator(path, true);
        path = StringUtils.stripStart(path, File.separator);
        path = StringUtils.stripEnd(path, File.separator);
        this.path = path;
        this.nodes = Stream.of(path.split("\\/")).map(Node::new).collect(Collectors.toList());
    }
    
    @Override
    public int compareTo(NumberPath o){
        for(int i = 0; i < Math.min(nodes.size(), o.nodes.size()); i++){
            int c = nodes.get(i).compareTo(o.nodes.get(i));
            if(c != 0) return c;
        }
        return nodes.size() - o.nodes.size();
    }

    /**
     * 每一层的对象
     * @author chanceylee on 2024年5月22日
     */
    @RequiredArgsConstructor
    static class Node implements Comparable<Node> {
        /** 路径名 */
        final String name;
        /** 路径名中提取的数字部分 */
        /**
         * 路径名中提取的数字部分，如果没有数字，则为null
         * 路径名可能是由多个数字部分组成的，比如 "vol 2-1.zip"，则会被解析为 [2,1]
         */
        final BigDecimal[] numbers;
        
        public Node(String name){
            super();
            this.name = name;
            this.numbers = parseNumbers(name);
        }
        
        static BigDecimal[] parseNumbers(String name){
            if(name == null) return null;
            Pattern pattern = Pattern.compile("\\d+");
            Matcher matcher = pattern.matcher(name);
            List<BigDecimal> numbers = new ArrayList<>();
            while (matcher.find()) {
                numbers.add(new BigDecimal(matcher.group()));
            }
            if(numbers.isEmpty()) return null;
            return numbers.toArray(new BigDecimal[0]);
        }
        
        @Override
        public int compareTo(Node n){
            if(numbers != null && n.numbers!=null) return NumbersComparator.compare(numbers, n.numbers);
            if(numbers != null) return -1;
            if(n.numbers != null) return 1;
            return name.compareTo(n.name);
        }
        
    }
    

    /**
     * 多数字部分对比期，比如一个文件夹中可能有文件
     * vol 1.zip
     * vol 2-1.zip
     * vol 2-2.zip
     * vol 3.zip
     * vol 3-1.zip
     * 则这些文件的数字部分分别为
     * [1]
     * [2,1]
     * [2,2]
     * [3]
     * [3,1]
     * 对比时，[3]应在[2,1]与[2,2]之后，[2,2]应在[2,1]之后，[3,1]应在[3]之后
     */
    static final Comparator<BigDecimal[]> NumbersComparator = (a, b)->{
        for(int i = 0; i < Math.min(a.length, b.length); i++){
            if(a[i] != b[i]) return a[i].compareTo(b[i]);
        }
        return a.length - b.length;
    };
    
    @Override
    public String toString(){
        return path;
    }
    
}
