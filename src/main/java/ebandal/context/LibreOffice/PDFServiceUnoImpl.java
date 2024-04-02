package ebandal.context.LibreOffice;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sun.star.beans.PropertyValue;
import com.sun.star.bridge.UnoUrlResolver;
import com.sun.star.bridge.XUnoUrlResolver;
import com.sun.star.comp.helper.Bootstrap;
import com.sun.star.frame.XComponentLoader;
import com.sun.star.frame.XStorable;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XMultiComponentFactory;
import com.sun.star.ucb.XFileIdentifierConverter;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XComponentContext;
import com.sun.star.util.XCloseable;

import ebandal.context.ConvertService.ProgressCallback;
import ebandal.context.conv.ConvertContext;
import ebandal.controller.ControllerStopException;
import ebandal.util.WslEnvSetup;

@Component("UNO")
public class PDFServiceUnoImpl implements PDFService {
    private static final Logger log = Logger.getLogger(PDFServiceUnoImpl.class.getName());

    @Value("${spring.profiles.active}")
    private String activeProfile;
    
    @Override
    public String getMode() {
        return "UNO";
    }

    @Override
    public void convert(ConvertContext context, String inputFile, String targetPath, ProgressCallback callback) 
                                                            throws IOException, InterruptedException, ControllerStopException {
        final String resolveURL = "uno:socket,host=127.0.0.1,port=" + context.port + ";urp;StarOffice.ComponentContext";
        String fileName = Paths.get(inputFile).getFileName().toString();
        String ext = fileName.replaceAll(".*\\.([^\\.]+)$", "$1").toLowerCase();
        String outputFile = Paths.get(targetPath, fileName.replaceAll(ext, "pdf")).toString();  //  Path를 사용하지 않고, 만들어낼 방법을 찾아야 할듯...
        String envOutputFile = outputFile;
        
        
        if (activeProfile.startsWith("wsl")) {
            inputFile = WslEnvSetup.convertWslPath(inputFile);
            envOutputFile = WslEnvSetup.convertWslPath(envOutputFile);
        }
        
        try {
            XComponentContext xcomponentcontext = Bootstrap.createInitialComponentContext(null);
            XUnoUrlResolver urlResolver = UnoUrlResolver.create(xcomponentcontext);
            
            Object initialObject = urlResolver.resolve(resolveURL);
            if (initialObject == null) {
                throw new ServiceNotFoundException();
            }
           
            XComponentContext mContext = (XComponentContext) UnoRuntime.queryInterface(XComponentContext.class, initialObject);
            XMultiComponentFactory mMCF = mContext.getServiceManager();
            Object oDesktop = mMCF.createInstanceWithContext("com.sun.star.frame.Desktop", mContext);
            
            XComponentLoader xCLoader = UnoRuntime.queryInterface(XComponentLoader.class, oDesktop);
            
            XFileIdentifierConverter xFileConverter = UnoRuntime.queryInterface(XFileIdentifierConverter.class,
                                                mMCF.createInstanceWithContext("com.sun.star.ucb.FileContentProvider", mContext));
            String import_path = xFileConverter.getFileURLFromSystemPath("",inputFile);
            String export_path = xFileConverter.getFileURLFromSystemPath("",envOutputFile);
            
            PropertyValue[] loadProps = new PropertyValue[1];
            PropertyValue[] saveProps = new PropertyValue[2];
            loadProps[0] = new PropertyValue();
            saveProps[0] = new PropertyValue();
            saveProps[1] = new PropertyValue();
            saveProps[0].Name = "Overwrite";
            saveProps[0].Value = true;
            loadProps[0].Name = "FilterName";
            saveProps[1].Name = "FilterName";
            switch(ext) {
            case "docx":
            case "doc":
                loadProps[0].Value = "Word 2007–365";
                saveProps[1].Value = "writer_pdf_Export";
                break;
            case "xlsx":
            case "xls":
                loadProps[0].Value = "Excel 2007–365";
                saveProps[1].Value = "calc_pdf_Export";
                break;
            case "pptx":
            case "ppt":
                loadProps[0].Value = "PowerPoint 2007–365";
                saveProps[1].Value = "impress_pdf_Export";
                break;
            case "hwp":
                loadProps[0].Value = "Hwp2002_Writer";
                saveProps[1].Value = "writer_pdf_Export";
            }
            
            long elapseStart = System.currentTimeMillis();
            log.info("UNO Conerting Document to PDF starting");
            XComponent xComp = xCLoader.loadComponentFromURL(import_path, "_default", 0, loadProps);
            XStorable xStorable = (XStorable) UnoRuntime.queryInterface(XStorable.class, xComp);
            xStorable.storeToURL(export_path, saveProps);
            long elapseEnd = System.currentTimeMillis();
            log.info("UNO Conerting Document to PDF completed: " + (elapseEnd-elapseStart)/1000 + " seconds");

            XCloseable xCloseable = (XCloseable) UnoRuntime.queryInterface(XCloseable.class, xComp);
            xCloseable.close(false);
            
        } catch (com.sun.star.connection.NoConnectException e) {
            throw new ServiceNotFoundException();
        } catch (java.lang.Exception e) {
            e.printStackTrace();
        }
    }

}
