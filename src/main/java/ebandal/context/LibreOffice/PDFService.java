package ebandal.context.LibreOffice;

import java.io.IOException;

import ebandal.context.ConvertService.ProgressCallback;
import ebandal.context.conv.ConvertContext;
import ebandal.controller.ControllerStopException;

public interface PDFService {

    public void convert(ConvertContext context, String sourceFile, String targetFile, ProgressCallback callback)
            throws IOException, InterruptedException, ControllerStopException, ServiceNotFoundException ;
    
    public String getMode();
}
