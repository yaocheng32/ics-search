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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;

/** Index all text files under a directory.
 * <p>
 * This is a command-line application demonstrating simple Lucene indexing.
 * Run it with no command-line arguments for usage information.
 */
public class IndexFiles {
  
  private IndexFiles() {}

  /** Index all text files under a directory. */
  public static void main(String[] args) {
    
    // Safety lock
    boolean run = true;
    if (!run) {
      return;
    }
    
    // True if to remove old index and create a new one; false if update
    boolean create = true;
    // Set index store path
    String indexPath = "index";
    // Set document file path
    String docsPath = "/Users/yaocheng/Desktop/Index_source_new/";
    // Set url table file name
    String tablePath = "/Users/yaocheng/Desktop/Index_source_new/table_url_list.txt";
    // Start building index
    Date start = new Date();
    try {
      System.out.println("Indexing to directory '" + indexPath + "'...");

      Directory dir = FSDirectory.open(new File(indexPath));
      Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_40); // use standard analyzer
      IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_40, analyzer);

      if (create) {
        // Create a new index in the directory, removing any
        // previously indexed documents:
        iwc.setOpenMode(OpenMode.CREATE);
      } else {
        // Add new documents to an existing index:
        iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
      }

      // Optional: for better indexing performance, if you
      // are indexing many documents, increase the RAM
      // buffer.  But if you do this, increase the max heap
      // size to the JVM (eg add -Xmx512m or -Xmx1g):
      //
      // iwc.setRAMBufferSizeMB(256.0);

      IndexWriter writer = new IndexWriter(dir, iwc);
      indexDocs(writer, docsPath, tablePath);

      // NOTE: if you want to maximize search performance,
      // you can optionally call forceMerge here.  This can be
      // a terribly costly operation, so generally it's only
      // worth it when your index is relatively static (ie
      // you're done adding documents to it):
      //
      // writer.forceMerge(1);

      writer.close();

      Date end = new Date();
      System.out.println(end.getTime() - start.getTime() + " total milliseconds");

    } catch (IOException e) {
      System.out.println(" caught a " + e.getClass() +
       "\n with message: " + e.getMessage());
    }
  }


  /**
   * Add a url and its content to the index.
   * 
   * @param writer Writer to the index where the given file/dir info will be stored
   * @param url The url string
   * @param url_text_path Content file of the url
   */
  static private void addDoc(IndexWriter writer, String url, String docsPath, String fileName) {
    Document doc = new Document();
    try {
      // add url
      doc.add(new StringField("url", url, Field.Store.YES));
      // add contents
      FileInputStream fis = new FileInputStream(docsPath+"Textdata/"+fileName);
      doc.add(new TextField("contents", new BufferedReader(new InputStreamReader(fis, "UTF-8"))));
      // add title
      String title = HtmlParser.getTitle(docsPath+"Htmldata/"+fileName);
      doc.add(new TextField("title", title, Field.Store.YES));
      // add length
      File f = new File(docsPath+"Textdata/"+fileName);
      doc.add(new LongField("length", f.length(), Field.Store.YES));
      
      // Document-level boost
      //doc.setBoost(1.0f);
      
      if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
        // New index, so we just add the document (no old document can be there):
        System.out.println("adding " + url);
        writer.addDocument(doc);
      } else {
        // Existing index (an old copy of this document may have been indexed) so 
        // we use updateDocument instead to replace the old one matching the exact 
        // path, if present:
        System.out.println("updating " + url);
        writer.updateDocument(new Term("url", url), doc);
      }
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
  }

  
  static private boolean filterUrl(String url) {
    if (url.matches("http://www\\.ics\\.uci\\.edu/~develop/.*")) {
      return true;
    }
    if (url.endsWith("/feed/")) {
      return true;
    }
    if (url.indexOf("feed=rss") >= 0) {
      return true;
    }
    if (url.indexOf(":8080") >= 0) {
      return true;
    }
    if (url.indexOf("http://galen.ics.uci.edu/") >= 0) {
      return true;
    }
    if (url.indexOf("/javadoc/") >= 0) {
      return true;
    }
    return false;
  }
  
  static private boolean tooSmall(String filepath) {
    File f = new File(filepath);
    return f.length() < 750;
  }

  /**
   * Indexes the given file using the given writer, or if a directory is given,
   * recurses over files and directories found under the given directory.
   * 
   * NOTE: This method indexes one document per input file.  This is slow.  For good
   * throughput, put multiple documents into your input file(s).  An example of this is
   * in the benchmark module, which can create "line doc" files, one document per line,
   * using the
   * <a href="../../../../../contrib-benchmark/org/apache/lucene/benchmark/byTask/tasks/WriteLineDocTask.html"
   * >WriteLineDocTask</a>.
   *  
   * @param writer Writer to the index where the given file/dir info will be stored
   * @param docsPath Path of source documents
   * @param tablePath Path of url file name table
   * @throws IOException If there is a low-level I/O error
   */
  static void indexDocs(IndexWriter writer, String docsPath, String tablePath)
    throws IOException {
    
    try {
      // Read url table file
      BufferedReader tableIn = new BufferedReader(new InputStreamReader(new FileInputStream(tablePath), "UTF-8"));
      String line;
      //int maxLines = 50; // Note: for now, index only maxLines files
      int counter = 0;
      while ( (line=tableIn.readLine()) != null) {
        String tline = line.trim();
        if (tline.length() == 0) continue;
        
        // Parse url & filename
        String[] tokens = tline.split("\\s+");
        String page_url = tokens[0];
        String page_textfile = tokens[1];
        
        // Filter useless urls
        if (filterUrl(page_url)) {
          continue;
        }
        
        // Filter nofollow
//        if (!HtmlParser.toFollow(docsPath+"Htmldata/"+page_textfile)) {
//          continue;
//        }
        
        // Filter small file
//        if (tooSmall(docsPath+"Textdata/"+page_textfile)) {
//          continue;
//        }
        
        // Add this url and its contents to index
        addDoc(writer, page_url, docsPath, page_textfile);
        
        counter++;
        System.out.println("File # " +counter+ " OK!");
        //if (counter == maxLines)
        //  break;
      }
      tableIn.close();
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }

  }
}
