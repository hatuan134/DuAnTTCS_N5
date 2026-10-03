package com.duanttcsn5.library;

import com.duanttcsn5.library.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.jpa.repository.Query;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the ACTUAL repository SQL on PostgreSQL with TEMP tables; rolls back all test data.
 * Run after Flyway V16, with S2_05_5_DB_URL + DB_USERNAME + DB_PASSWORD.
 */
@EnabledIfEnvironmentVariable(named = "S2_05_5_DB_URL", matches = ".+")
class PublicCatalogOptimizedQueryTest {
    @Test void sqlPreservesMatchingRankingFiltersAvailabilityAndStablePages() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getenv("S2_05_5_DB_URL"),
                System.getenv().getOrDefault("DB_USERNAME", "postgres"), System.getenv("DB_PASSWORD"))) {
            c.setAutoCommit(false);
            try {
                try (var s = c.createStatement()) {
                    s.execute("CREATE TEMP TABLE authors(id bigint PRIMARY KEY, name text, name_search text GENERATED ALWAYS AS (catalog_search_text(name)) STORED) ON COMMIT DROP");
                    s.execute("CREATE TEMP TABLE books(id bigint PRIMARY KEY, title text, isbn text, author_id bigint, category_id bigint, publication_year integer, title_search text GENERATED ALWAYS AS (catalog_search_text(title)) STORED, isbn_search text GENERATED ALWAYS AS (catalog_search_text(isbn)) STORED, isbn_digits text GENERATED ALWAYS AS (catalog_isbn_digits(isbn)) STORED) ON COMMIT DROP");
                    s.execute("CREATE TEMP TABLE book_authors(book_id bigint, author_id bigint) ON COMMIT DROP");
                    s.execute("CREATE TEMP TABLE book_copies(id bigint PRIMARY KEY, book_id bigint, status text) ON COMMIT DROP");
                    s.execute("CREATE TEMP TABLE loan_items(book_copy_id bigint, returned_at timestamptz) ON COMMIT DROP");
                    s.execute("INSERT INTO authors VALUES(1,'Khác'),(2,'Mắt biếc'),(3,'Nguyễn Nhật Ánh'),(4,'Nguyễn Nhật Ánh và cộng sự')");
                    s.execute("INSERT INTO books(id,title,isbn,author_id,category_id,publication_year) VALUES (1,'Mắt biếc bản đặc biệt',NULL,1,6,2000),(2,'Mắt biếc',NULL,1,6,2000),(3,'Mắt biếc',NULL,2,6,2000),(4,'A',NULL,4,6,2008),(5,'B',NULL,3,6,2008),(6,'C',NULL,3,6,2008),(7,'ISBN exact','978-604-2-00001-1',1,6,2008),(8,'ISBN partial','978604200001199',1,6,2008),(9,'100%_literal',NULL,1,2,NULL),(10,'Hidden Mắt biếc',NULL,1,6,2008)");
                    s.execute("INSERT INTO book_authors VALUES(6,3),(6,4)");
                    s.execute("INSERT INTO book_copies SELECT id,id,'AVAILABLE' FROM books WHERE id <> 10");
                    s.execute("INSERT INTO book_copies VALUES(11,2,'BORROWED'),(12,2,'REPAIR'),(13,2,'HELD'),(14,2,'REMOVED')");
                    s.execute("INSERT INTO loan_items VALUES(3,NULL),(2,CURRENT_TIMESTAMP)");
                }
                assertEquals(List.of(3L,2L,1L), ids(c,"mat biec","",null,null,false,"relevance",20,0));
                assertEquals(List.of(5L,6L,4L), ids(c,"nguyen nhat anh","",null,null,false,"relevance",20,0));
                assertEquals(List.of(7L,8L), ids(c,"978 604 2 00001 1","9786042000011",null,null,false,"relevance",20,0));
                assertEquals(List.of(9L), ids(c,"%_","",null,null,false,"relevance",20,0));
                assertEquals(List.of(2L,1L), ids(c,"mat biec","",6L,2000,true,"publicationYear",20,0));
                assertTrue(ids(c,"mat biec","",2L,2000,false,"relevance",20,0).isEmpty());
                assertEquals(List.of(4L,5L,6L,7L,8L,2L,3L,1L,9L), ids(c,"","",null,null,false,"publicationYear",20,0));
                var all = ids(c,"","",null,null,false,"relevance",20,0);
                var joined = new ArrayList<Long>();
                for (int offset = 0; offset < 9; offset += 3) joined.addAll(ids(c,"","",null,null,false,"relevance",3,offset));
                assertEquals(all, joined); assertEquals(9, new HashSet<>(joined).size());
                assertEquals(ids(c,"","",null,null,false,"relevance",3,3), ids(c,"","",null,null,false,"relevance",3,3));
                Map<String,Object> params = params("mat biec","",6L,2000,true,"relevance",20,0);
                String countSql = BookRepository.class.getMethod("countPublicSearch",String.class,String.class,Long.class,Integer.class,boolean.class).getAnnotation(Query.class).value();
                try (var s = prepare(c,countSql,params); var r = s.executeQuery()) { r.next(); assertEquals(2,r.getLong(1)); }
                try (var s = c.createStatement(); var r = s.executeQuery("SELECT catalog_search_text(U&'  MA\\0306\\0301T  BIE\\0302\\0301C  '), catalog_search_text('ĐẶNG'), catalog_isbn_digits('ISBN-X')")) {
                    r.next(); assertEquals("mat biec",r.getString(1)); assertEquals("dang",r.getString(2)); assertEquals("",r.getString(3));
                }
                // Generated fields also update on edits; no stale application-side search cache.
                try (var s = c.createStatement()) { s.execute("UPDATE authors SET name='Đặng Văn Mới' WHERE id=3"); }
                assertEquals(List.of(5L,6L), ids(c,"dang van moi","",null,null,false,"relevance",20,0));
            } finally { c.rollback(); }
        }
    }
    private List<Long> ids(Connection c,String text,String isbn,Long category,Integer year,boolean free,String sort,int limit,long offset) throws Exception {
        String sql = BookRepository.class.getMethod("findPublicSearchIds",String.class,String.class,Long.class,Integer.class,boolean.class,String.class,int.class,long.class).getAnnotation(Query.class).value();
        var ids = new ArrayList<Long>();
        try (var s=prepare(c,sql,params(text,isbn,category,year,free,sort,limit,offset));var r=s.executeQuery()) { while(r.next()) ids.add(r.getLong(1)); }
        return ids;
    }
    private Map<String,Object> params(String text,String isbn,Long category,Integer year,boolean free,String sort,int limit,long offset) {
        var p=new HashMap<String,Object>(); p.put("text",text);p.put("isbn",isbn);p.put("categoryId",category);p.put("publicationYear",year);p.put("availableOnly",free);p.put("sort",sort);p.put("limit",limit);p.put("offset",offset);return p;
    }
    private PreparedStatement prepare(Connection c,String sql,Map<String,Object> params) throws Exception {
        var matcher=Pattern.compile(":([a-zA-Z]+)").matcher(sql);var names=new ArrayList<String>();var out=new StringBuilder();
        while(matcher.find()){names.add(matcher.group(1));matcher.appendReplacement(out,"?");}matcher.appendTail(out);
        var statement=c.prepareStatement(out.toString());
        for(int i=0;i<names.size();i++) statement.setObject(i+1,params.get(names.get(i)));
        return statement;
    }
}
