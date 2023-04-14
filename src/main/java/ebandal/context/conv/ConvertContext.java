package ebandal.context.conv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConvertContext {
	private static final Logger log = LoggerFactory.getLogger(ConvertContext.class);

	public String docName;
	public String extName;
	public PdfCtx pdfCtx;
	public String lastErrMsg;
	public String pdfPath;
	public int    splitSize;
    public int    timeoutSec;
    
    // binary LibreOffice convert
    public String sofficePath;
    // UNO API convert
    public int    port;
    
	
	public static class PdfCtx {
		String title;
		String author;
		String publisher;
		String publishDate;
		String isbn;
		String language;
		String subject;
		public int    pages;
		
		public HashMap<Integer, Rect> rectMap;
		
		public String getTitle() {
			return title==null?"":title;
		}
		public void setTitle(String title) {
			this.title = title;
		}
		public String getAuthor() {
			return author==null?"":author;
		}
		public void setAuthor(String author) {
			this.author = author;
		}
		public String getPublisher() {
			return publisher==null?"":publisher;
		}
		public void setPublisher(String publisher) {
			this.publisher = publisher;
		}
		public String getPublishDate() {
			return publishDate==null?"":publishDate;
		}
		public void setPublishDate(String publishDate) {
			this.publishDate = publishDate;
		}
		public String getIsbn() {
			return isbn==null?"":isbn;
		}
		public void setIsbn(String isbn) {
			this.isbn = isbn;
		}
		public String getLanguage() {
			return language==null?"":language;
		}
		public void setLanguage(String language) {
			this.language = language;
		}
		public String getSubject() {
			return subject==null?"":subject;
		}
		public void setSubject(String subject) {
			this.subject = subject;
		}
		public int getPages() {
			return pages;
		}
		public void setPages(int pages) {
			this.pages = pages;
		}
	}
	
	public static class Rect {
	    public double widthPts;
	    public double heightPts;
	    public void setWidthPts(double widthPts) {
	        this.widthPts = widthPts;
	    }
        public void setHeightPts(double heightPts) {
            this.heightPts = heightPts;
        }
        double getWidthPts() {
            return widthPts;
        }
        double getHeightPts() {
            return heightPts;
        }
        double getWidthInch() {
            return widthPts*0.0138889;
        }
        double getHeightInch() {
            return heightPts*0.0138889;
        }
        double getWidthCm() {
            return widthPts*0.0352778;
        }
        double getHeightCm() {
            return heightPts*0.0352778;
        }
        public int getHpixel() {
            return (widthPts>0&&heightPts>0)?1240:0;
        }
        public int getVpixel() {
            return (int)(getHpixel()*(heightPts/widthPts));
        }
	}
	
	public static class ProcessStreamCatcher implements Runnable {
		InputStream ins;
		String charset;
		MatchCallback callback;
		
		public ProcessStreamCatcher(InputStream ins) {
			this.ins = ins;
			this.charset = "UTF-8";
		}
		public ProcessStreamCatcher(InputStream ins, MatchCallback callback) {
		    this.ins = ins;
		    this.charset = "UTF-8";
		    this.callback = callback;
		}
		public ProcessStreamCatcher(InputStream ins, String charset, MatchCallback callback) {
			this.ins = ins;
			this.charset = charset;
			this.callback = callback;
		}
		@Override
		public void run() {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(ins, charset))) {
				String line;
				while((line = br.readLine())!=null) {
					// log.debug(line);
					if (callback!=null) {
						callback.onReceive(line);
					}
				}
				ins.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}
	
	public static interface MatchCallback {
		public void onReceive(String line);
	}

	public void deleteFolderIfExists(Path path) {
		try {
    	if (path.toFile().exists()) {
        	// 하위폴더
    		Files.walk(path)
	    	     .sorted((p1, p2) -> {
					if (p1.getNameCount() > p2.getNameCount()) 
						return -1;
					else if (p1.getNameCount() < p2.getNameCount())
						return 1;
					else {
						return p1.toString().compareTo(p2.toString());
					}
	    	     })
				.forEach(p -> {
					try {
						log.debug("deleting " + p.getFileName().toString());
						Files.deleteIfExists(p);
					} catch (IOException e) {
						log.error(e.getMessage());
					}
				});
    		}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	public static class ParamCallback {
		public void add(String arg) { };
		public void addCommand() { };
		public String convertedPath(String documentPath) { return documentPath; };
	}
	

}
