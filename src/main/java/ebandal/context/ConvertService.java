package ebandal.context;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ebandal.context.LibreOffice.PDFService;
import ebandal.context.LibreOffice.SOfficeProcessManager;
import ebandal.context.LibreOffice.ServiceNotFoundException;
import ebandal.context.conv.ConvertContext;
import ebandal.controller.ControllerStopException;
import ebandal.controller.ErrCode;

@Component
public class ConvertService {
    private static final Logger log = LoggerFactory.getLogger(ConvertService.class);

    private Object lockLibreOffice = new Object();

    @Value("${conv.command.libreoffice}")
    private String libreoffice;

    @Value("${conv.input.directory}")
    private String inputPath;

    @Value("${conv.output.directory}")
    private String outputPath;

    @Value("${conv.wait.seconds}")
    private String waitDurationStr;

    @Value("${conv.split.step}")
    private String convSplitPages;

    @Autowired
    SOfficeProcessManager bgProcess;

    @Autowired
    @Qualifier("BIN")
    PDFService binPdfService;

    @Autowired
    @Qualifier("UNO")
    PDFService unoPdfService;

    public static HashMap<String, Progress> progressMap = new HashMap<String, Progress>();

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(inputPath));
            Files.createDirectories(Paths.get(outputPath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * convert from Controller
     */
    public boolean convert(Path sourcePath, ProgressCallback prgrssCB)
            throws ControllerStopException, IOException, InterruptedException {

        ConvertContext context = new ConvertContext();

        context.docName = sourcePath.getFileName().toString().replaceAll("\\.[^\\.]+$", "");
        context.extName = sourcePath.getFileName().toString().replaceAll(".*\\.([^\\.]+)$", "$1").toLowerCase();
        context.timeoutSec = Integer.valueOf(waitDurationStr);

        prgrssCB.key = context.docName;
        prgrssCB.isPdf = context.extName.equals("pdf");

        Progress prog = progressMap.get(prgrssCB.key);
        if (prog == null) {
            prog = new Progress();
        }
        prog.curPages = -1;
        prog.totPages = -1;
        progressMap.put(prgrssCB.key, prog);

        if (context.extName.toLowerCase().matches("(xls|xlsx|ppt|pptx|doc|docx|hwp|hwpx)")) {
            try {
                context.port = 10002;
                unoPdfService.convert(context, sourcePath.toString(), outputPath, prgrssCB);
            } catch (ServiceNotFoundException e) {
                synchronized (lockLibreOffice) {
                    context.sofficePath = libreoffice;
                    binPdfService.convert(context, sourcePath.toString(), outputPath, prgrssCB);
                }
            }

            Path p = Paths.get(outputPath, context.docName + ".pdf");
            context.pdfPath = p.toString();
            File f = p.toFile();
            if (f.exists() == false) {
                throw new ControllerStopException(ErrCode.SOFFICE_PROCESS_TERMINATED_IOEXCEPTION);
            }
            progressMap.put(prgrssCB.key, prog);

        } else if (context.extName.toLowerCase().matches("pdf")) {
            context.pdfPath = sourcePath.toString();
        }

        if (prgrssCB.lastErrMsg == null || prgrssCB.lastErrMsg.toLowerCase().equals("success")) {
            return true;
        } else {
            return false;
        }
    }

    public static class Progress {
        int curPages;
        int totPages;
    }

    public static class ProgressCallback {

        public boolean isPdf;
        public String key;
        public String lastErrMsg;

        public ProgressCallback() {
        }

        public ProgressCallback(String key, boolean isPdf) {
            this.key = key;
            this.isPdf = isPdf;
        }

        public void onProgress(int curPage) {
            Progress prog = progressMap.get(key);
            if (prog == null) {
                prog = new Progress();
            }
            prog.curPages = curPage;
            progressMap.put(key, prog);
        }
    }

}
