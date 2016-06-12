package edu.uci.ics.searcher;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.lucene.queries.CustomScoreProvider;
import org.apache.lucene.queries.CustomScoreQuery;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.Explanation;
//import org.apache.lucene.index.FieldInvertState;
//import org.apache.lucene.search.similarities.Similarity;

/** Simple command-line based search demo. */
public class SearchFiles {

  private SearchFiles() {}

  /** Simple command-line based search demo. */
  public static void main(String[] args) throws Exception {
    String usage =
      "Usage:\tjava org.apache.lucene.demo.SearchFiles [-index dir] [-field f] [-repeat n] [-queries file] [-query string] [-raw] [-paging hitsPerPage]\n\nSee http://lucene.apache.org/core/4_1_0/demo/ for details.";
    if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
      System.out.println(usage);
      System.exit(0);
    }

    String index = "index";
    String field = "contents";
    String queries = null;
    int repeat = 0;
    boolean raw = false;
    String queryString = null;
    int hitsPerPage = 10;
    
    for(int i = 0;i < args.length;i++) {
      if ("-index".equals(args[i])) {
        index = args[i+1];
        i++;
      } else if ("-field".equals(args[i])) {
        field = args[i+1];
        i++;
      } else if ("-queries".equals(args[i])) {
        queries = args[i+1];
        i++;
      } else if ("-query".equals(args[i])) {
        queryString = args[i+1];
        i++;
      } else if ("-repeat".equals(args[i])) {
        repeat = Integer.parseInt(args[i+1]);
        i++;
      } else if ("-raw".equals(args[i])) {
        raw = true;
      } else if ("-paging".equals(args[i])) {
        hitsPerPage = Integer.parseInt(args[i+1]);
        if (hitsPerPage <= 0) {
          System.err.println("There must be at least 1 hit per page.");
          System.exit(1);
        }
        i++;
      }
    }
    
    IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(index)));
    IndexSearcher searcher = new IndexSearcher(reader);
    Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_40);

    BufferedReader in = null;
    if (queries != null) {
      in = new BufferedReader(new InputStreamReader(new FileInputStream(queries), "UTF-8"));
    } else {
      in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
    }
    QueryParser parser = new QueryParser(Version.LUCENE_40, field, analyzer);
    while (true) {
      if (queries == null && queryString == null) {                        // prompt the user
        System.out.println("Enter query: ");
      }

      String line = queryString != null ? queryString : in.readLine();

      if (line == null || line.length() == -1) {
        break;
      }

      line = line.trim();
      if (line.length() == 0) {
        break;
      }
      
      Query query = parser.parse(line);
      System.out.println("Searching for: " + query.toString(field));
            
      if (repeat > 0) {                           // repeat & time as benchmark
        Date start = new Date();
        for (int i = 0; i < repeat; i++) {
          searcher.search(query, null, 100);
        }
        Date end = new Date();
        System.out.println("Time: "+(end.getTime()-start.getTime())+"ms");
      }

      doPagingSearch(in, searcher, query, hitsPerPage, raw, queries == null && queryString == null);

      if (queryString != null) {
        break;
      }
    }
    reader.close();
  }

  /**
   * This demonstrates a typical paging search scenario, where the search engine presents 
   * pages of size n to the user. The user can then go to the next page if interested in
   * the next hits.
   * 
   * When the query is executed for the first time, then only enough results are collected
   * to fill 5 result pages. If the user wants to page beyond this limit, then the query
   * is executed another time and all hits are collected.
   * 
   */
  public static void doPagingSearch(BufferedReader in, IndexSearcher searcher, Query query, 
                                     int hitsPerPage, boolean raw, boolean interactive) throws IOException {
 
    // Collect enough docs to show 5 pages
    TopDocs results = searcher.search(query, 5 * hitsPerPage);
    ScoreDoc[] hits = results.scoreDocs;
    
    int numTotalHits = results.totalHits;
    System.out.println(numTotalHits + " total matching documents");

    int start = 0;
    int end = Math.min(numTotalHits, hitsPerPage);
        
    while (true) {
      if (end > hits.length) {
        System.out.println("Only results 1 - " + hits.length +" of " + numTotalHits + " total matching documents collected.");
        System.out.println("Collect more (y/n) ?");
        String line = in.readLine();
        if (line.length() == 0 || line.charAt(0) == 'n') {
          break;
        }

        hits = searcher.search(query, numTotalHits).scoreDocs;
      }
      
      end = Math.min(hits.length, start + hitsPerPage);
      
      for (int i = start; i < end; i++) {
        if (raw) {                              // output raw format
          System.out.println("doc="+hits[i].doc+" score="+hits[i].score);
          continue;
        }

        Document doc = searcher.doc(hits[i].doc);
        String url = doc.get("url");
        if (url != null) {
          System.out.println((i+1) + ". " + url);
          String title = doc.get("title");
          if (title != null) {
            System.out.println("   Title: " + doc.get("title"));
          }
        } else {
          System.out.println((i+1) + ". " + "No path for this document");
        }
                  
      }

      if (!interactive || end == 0) {
        break;
      }

      if (numTotalHits >= end) {
        boolean quit = false;
        while (true) {
          System.out.print("Press ");
          if (start - hitsPerPage >= 0) {
            System.out.print("(p)revious page, ");  
          }
          if (start + hitsPerPage < numTotalHits) {
            System.out.print("(n)ext page, ");
          }
          System.out.println("(q)uit or enter number to jump to a page.");
          
          String line = in.readLine();
          if (line.length() == 0 || line.charAt(0)=='q') {
            quit = true;
            break;
          }
          if (line.charAt(0) == 'p') {
            start = Math.max(0, start - hitsPerPage);
            break;
          } else if (line.charAt(0) == 'n') {
            if (start + hitsPerPage < numTotalHits) {
              start+=hitsPerPage;
            }
            break;
          } else {
            int page = Integer.parseInt(line);
            if ((page - 1) * hitsPerPage < numTotalHits) {
              start = (page - 1) * hitsPerPage;
              break;
            } else {
              System.out.println("No such page");
            }
          }
        }
        if (quit) break;
        end = Math.min(numTotalHits, start + hitsPerPage);
      }
    }
  }

  
  private static Query myQuery(String query_string) throws Exception {
    String field = "contents";
    Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_40);
    QueryParser parser = new QueryParser(Version.LUCENE_40, field, analyzer);
    Query query = parser.parse(query_string);
    return query;
  }
  
  private static Query myBooleanQuery(String query_string) throws Exception {
    Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_40);
    BooleanQuery bq = new BooleanQuery();
    
    String[] query_tokens = query_string.trim().split(" ");
    PhraseQuery contents_phrase_query = new PhraseQuery();
    PhraseQuery title_phrase_query = new PhraseQuery();
    for (int i = 0; i < query_tokens.length; i++) {
      contents_phrase_query.add(new Term("contents", query_tokens[i]), i);
      title_phrase_query.add(new Term("title", query_tokens[i]), i);
    }
    contents_phrase_query.setBoost(2.0f);
    title_phrase_query.setBoost(2.0f);
    bq.add(contents_phrase_query, BooleanClause.Occur.SHOULD);
    bq.add(title_phrase_query, BooleanClause.Occur.SHOULD);
    
    TermQuery contents_term_query = new TermQuery(new Term("contents", query_string));
    //contents_term_query.setBoost(1.5f);
    bq.add(contents_term_query, BooleanClause.Occur.SHOULD);
    TermQuery title_term_query = new TermQuery(new Term("title", query_string));
    bq.add(title_term_query, BooleanClause.Occur.SHOULD);
    
    //TermQuery pub_query = new TermQuery(new Term("contents", "publications"));
    //bq.add(pub_query, BooleanClause.Occur.SHOULD);
    
    //FuzzyQuery url_query = new FuzzyQuery(new Term("url", "www.ics.uci.edu"), 2);
    //bq.add(url_query, BooleanClause.Occur.SHOULD);
    
    Query query = new QueryParser(Version.LUCENE_40, "contents", analyzer).parse(bq.toString());
    return query;
  }
  
  public static String[] getTopSearchResults(String query_string, int num_of_results) throws Exception {
    // Read index
    String index = "index";
    IndexReader idxreader = DirectoryReader.open(FSDirectory.open(new File(index)));
    
    // Init searcher of the index
    IndexSearcher searcher = new IndexSearcher(idxreader);
    
    // Set up query
    Query query = myBooleanQuery(query_string);
    
    // Addition scoring query
    CustomScoreQuery myCustomQuery = new MyOwnScoreQuery(query);
    
    //TopDocs results = searcher.search(query, num_of_results);
    TopDocs results = searcher.search(myCustomQuery.createWeight(searcher).getQuery(), num_of_results);
    ScoreDoc[] hits = results.scoreDocs;
    
    String[] top_results = new String[num_of_results];
    for (int i = 0; i < num_of_results; i++) {
      Document doc = searcher.doc(hits[i].doc);
      String url = doc.get("url");
      //System.out.println(url);
      top_results[i] = url;
    }
    return top_results;
  }
  
  static class MyOwnScoreQuery extends CustomScoreQuery {
    private Query query;

    public MyOwnScoreQuery(Query query) {
        super(query);
        this.query = query;
    }
    public CustomScoreProvider getCustomScoreProvider(final AtomicReaderContext reader) {
      return new CustomScoreProvider(reader) {
          @Override
          public float customScore(int doc, float subQueryScore, float valSrcScore) throws IOException {
              
              float score = subQueryScore;
              Document docObject = reader.reader().document(doc);

              String url = docObject.get("url");
              if (url.equals("http://www.ics.uci.edu/")) {
                //score *= 15;
              } else if (url.equals("http://www.ics.uci.edu/grad/")) {
                score *= 2;
              } else if (url.indexOf("http://www.ics.uci.edu/grad/courses/") >= 0) {
                score *= 2;
              } else if (url.indexOf("http://www.ics.uci.edu/grad/degrees/") >= 0) {
                score *= 2;
              } else if (url.indexOf("http://www.ics.uci.edu/grad/admissions/") >= 0) {
                score *= 2;
              } else if (url.indexOf("http://www.ics.uci.edu/grad/sao/") >= 0) {
                score *= 2;
              } else if (url.indexOf("http://www.ics.uci.edu/ugrad/sao/") >= 0) {
                score *= 2;
              } else if (url.equals("http://www.ics.uci.edu/ugrad/")) {
                score *= 2;
              } else if (url.indexOf("http://www.ics.uci.edu/prospective/en/degrees/") >= 0) {
                score *= 3;
              } else if (url.indexOf("http://www.ics.uci.edu/faculty/") >= 0) {
                score *= 1.5;
              } else if (url.equals("http://archive.ics.uci.edu/ml/")) {
                score *= 3;
              } else if (url.equals("http://archive.ics.uci.edu/ml/datasets.html")) {
                score *= 3;
              } else if (url.indexOf("http://mlearn.ics.uci.edu/") >= 0) {
                score *= 2;
              } else if (url.equals("http://cml.ics.uci.edu/")) {
                score *= 3;
              } else if (url.indexOf("http://www.ics.uci.edu/~fielding/") >= 0) {
                score *= 2;
              } 
              
              else if (url.indexOf("http://luci.ics.uci.edu/blog/?") >= 0) {
                score /= 5;
              } else if (url.indexOf("http://cgvw.ics.uci.edu/?") >= 0) {
                score /= 5;
              } else if (url.indexOf("http://fano.ics.uci.edu/") >= 0) {
                score /= 2;
              } else if (url.indexOf("http://www.ics.uci.edu/~eppstein/pix/") >= 0) {
                score /= 20; // REST
              } else if (url.indexOf("http://vcp.ics.uci.edu/content/") >= 0) {
                score /= 10; // REST
              }
              
              
              long len = Long.parseLong(docObject.get("length"));  
              if (len <= 1500) {
                if (!url.equals("http://mlearn.ics.uci.edu/")) {
                  score /= (20/Math.log10(len));
                  //score /= 3;
                }
              }
              
              IndexSearcher searcher = new IndexSearcher(reader.reader()); 
              if (url.indexOf("http://www.ics.uci.edu/~johannab/the-rest.html") >= 0) {
                Explanation exp = searcher.explain(query, doc);
                //System.out.println(exp);
              }
              return score;
          }
      };
    }
  }
}
