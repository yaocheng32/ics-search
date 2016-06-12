package edu.uci.ics.searcher;

import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;
import java.io.File;
import java.util.*;

public class HtmlParser {

  /**
   * @param args
   */
  public static void main(String[] args) {
    String html = "<html><head><title>First parse</title><meta name=\"robots\" content=\"noindex, nofollow\" /><meta name=\"keywords\" content=\"HTML,CSS,XML,JavaScript\" /></head>"
        + "<body><p>Parsed HTML into a doc.</p></body></html>";
    
      Document doc = Jsoup.parse(html);
      //Elements meta = doc.getElementsByTag("meta[name=robots]");
      Elements meta = doc.head().select("meta[name=robots]");
      //System.out.println(meta.isEmpty());
      //System.out.println(doc.head().text());
      Iterator<Element> itr = meta.iterator();
      while (itr.hasNext()) {
        System.out.println(itr.next().attr("content"));
      }
      //System.out.println("123");
      //System.out.println(doc.text());
      //System.out.println(doc.title());
  }
  
  public static boolean toFollow(String filepath) {
    try {
      Document doc = Jsoup.parse(new File(filepath), "UTF-8");
      Elements meta = doc.head().select("meta[name=robots]");
      if (meta.isEmpty()) {
        return true;
      } else {
        String content = meta.first().attr("content").toLowerCase();
        if (content.indexOf("nofollow") >= 0 || content.indexOf("noindex") >= 0) {
          return false;
        }
      }
    } catch(Exception e) {
    }
    return true;
  }
  
  public static String getTitle(String filepath) {
    String title = "";
    try {
      Document doc = Jsoup.parse(new File(filepath), "UTF-8");
      title = doc.title();
    } catch(Exception e) {
    }
    return title;
  }
}
