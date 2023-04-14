package ebandal.controller;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Configuration
@RestController
public class DownloadController {
    private static final Logger log = LoggerFactory.getLogger(DownloadController.class);
	
	@Value("${conv.input.directory}")
	private String pdfDownloadBase;

    @RequestMapping(value="/download", method=RequestMethod.GET )
	public ResponseEntity<InputStreamResource> download(@RequestParam(name = "key") String filename) throws IOException {
    	InputStreamResource resource;

    	// String filename = params.getKey();
    	log.info("/download request with key=" + filename);
    	try {
			if (filename == null || filename.equals("")) {
				log.error("/download request doesn't have key value!!!");
				throw new ControllerStopException(ErrCode.KEY_IS_EMPTY);
			}

			if (filename.endsWith("/")) {
				filename = filename.replaceAll("/$", "");
			}
			
			if (filename.endsWith(".pdf")==false) {
				filename += ".pdf";
			}
	
			File inputFolder = new File(pdfDownloadBase);
			File targetPdfFile = null;
			
			targetPdfFile = new File(inputFolder, filename);
			if (targetPdfFile.exists() == false) {
				log.error("File out of /download/key+.pdf ("+targetPdfFile.getAbsolutePath()+") doesn't exist!!!");
				throw new ControllerStopException(ErrCode.FILE_NOT_FOUND);
			}
			
	        HttpHeaders headers = new HttpHeaders();
	        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename="+URLEncoder.encode(filename, "UTF-8"));
	        headers.add("Cache-Control", "public, no-store, must-revalidate");
	        headers.add("Pragma", "public");
	        
		    resource = new InputStreamResource(new FileInputStream(targetPdfFile));

		    return ResponseEntity.ok()
		            			 .headers(headers)
		            			 .contentLength(targetPdfFile.length())
		            			 .contentType(MediaType.APPLICATION_PDF)
		            			 .body(resource);
		} catch (ControllerStopException e) {
			resource = new InputStreamResource(new ByteArrayInputStream(e.getMessage().getBytes()));
			return ResponseEntity.ok()
								 .headers(new HttpHeaders())
								 .body(resource);
		}
	}
    
    @RequestMapping(value="/viewPDF", method=RequestMethod.GET )
    public ResponseEntity<InputStreamResource> viewPDF(@RequestParam(name = "key") String filename) throws IOException {
        InputStreamResource resource;

        log.info("/viewPDF request with key=" + filename);
        try {
            if (filename == null || filename.equals("")) {
                log.error("/download request doesn't have key value!!!");
                throw new ControllerStopException(ErrCode.KEY_IS_EMPTY);
            }

            if (filename.endsWith("/")) {
                filename = filename.replaceAll("/$", "");
            }
            
            if (filename.endsWith(".pdf")==false) {
                filename += ".pdf";
            }
    
            File inputFolder = new File(pdfDownloadBase);
            File targetPdfFile = null;
            
            targetPdfFile = new File(inputFolder, filename);
            if (targetPdfFile.exists() == false) {
                log.error("File out of /download/key+.pdf ("+targetPdfFile.getAbsolutePath()+") doesn't exist!!!");
                throw new ControllerStopException(ErrCode.FILE_NOT_FOUND);
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename="+URLEncoder.encode(filename, "UTF-8"));
            headers.add("Cache-Control", "public, no-store, must-revalidate");
            headers.add("Pragma", "public");
            
            resource = new InputStreamResource(new FileInputStream(targetPdfFile));

            return ResponseEntity.ok()
                                 .headers(headers)
                                 .contentLength(targetPdfFile.length())
                                 .contentType(MediaType.APPLICATION_PDF)
                                 .body(resource);
        } catch (ControllerStopException e) {
            resource = new InputStreamResource(new ByteArrayInputStream(e.getMessage().getBytes()));
            return ResponseEntity.ok()
                                 .headers(new HttpHeaders())
                                 .body(resource);
        }
    }


}
