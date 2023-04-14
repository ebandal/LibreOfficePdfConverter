package ebandal.context.LibreOffice;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ebandal.context.conv.ConvertContext.MatchCallback;
import ebandal.context.conv.ConvertContext.ParamCallback;
import ebandal.context.conv.ConvertContext.ProcessStreamCatcher;
import ebandal.util.WslEnvSetup;

public class SOfficeProcessThread implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(SOfficeProcessThread.class);
    private String sofficePath;
    private int port = 10002;
    private String osEnv;

    public SOfficeProcessThread(String osEnv, String officePath, int port) {
        this.osEnv = osEnv;
        this.sofficePath = officePath;
        this.port = port;
    }
    
    private void makeArgs(ParamCallback callback, int port) {
        callback.addCommand();
        
        callback.add("--headless");
        callback.add("--invisible");
        callback.add("--nocrashreport");
        callback.add("--nodefault");
        callback.add("--nologo");
        callback.add("--nofirststartwizard");
        callback.add("--norestore");
        callback.add("-env:UserInstallation=file:///tmp/tempUser" + port);
        callback.add("--accept=\"socket,port=" + port + ",tcpNoDelay=1;urp;StarOffice.ComponentContext\""); 
    }
    
    public void run() {
        
        List<String> commandWithArgs = new ArrayList<String>();
        if (osEnv.equals("wsl")) {
            commandWithArgs.add("wsl");
            commandWithArgs.add("--user");  commandWithArgs.add("root");
            
            ParamCallback callback = new ParamCallback() {
                public void add(String arg) {
                    commandWithArgs.add(arg);
                }
                public void addCommand() {
                    commandWithArgs.add(sofficePath);
                }
                public String convertedPath(String path) {
                    return WslEnvSetup.convertWslPath(path);
                }
            };
            makeArgs(callback, this.port);
        } else if (osEnv.equals("linux")) {
            // bash -c 'mkdir -p /PDFCONV/disk/input/ & cd /PDFCONV/disk/input/ & soffice --headless --convert-to pdf:writer_pdf_Export /PDFCONV/disk/input/2021.pptx'
            String officePath = this.sofficePath;
            commandWithArgs.add("bash");        commandWithArgs.add("-c");
            StringBuffer argSb = new StringBuffer();
            
            ParamCallback callback = new ParamCallback() {
                public void add(String arg) {
                    argSb.append(arg + " ");
                }
                public void addCommand() {
                    argSb.append(officePath + " ");
                }
                public String convertedPath(String path) {
                    return path;
                }
            };
            makeArgs(callback, this.port);
            commandWithArgs.add(argSb.toString());
        }
        
        while(true) {
            try {
                Thread t1 = null;
                try {
                    log.info("LibreOffice background process: " + commandWithArgs.stream().collect(Collectors.joining(" ")));

                    ProcessBuilder builder = new ProcessBuilder();
                    builder.redirectErrorStream(true);
                    builder = builder.command(commandWithArgs);
                    if (osEnv.equals("linux")) {
                        builder = builder.directory(new File("."));
                    }

                    log.info("libreoffice headless (" + this.port + ") starting");

                    Process process = builder.start();
                    t1 = new Thread(new ProcessStreamCatcher(process.getInputStream(), new MatchCallback() {
                        @Override
                        public void onReceive(String line) {
                            log.info(line);;
                        }
                    }));
                    t1.start();
                    process.waitFor();
                    t1.join();
                    
                } catch (IOException e) {
                    log.info(e.getLocalizedMessage());
                    t1.join();
                }
                
                log.info("libreoffice headless (" + this.port + ") terminated!!!");
            } catch (InterruptedException e1) {
                log.info(e1.getLocalizedMessage());
            }
        }
        
    }

}
