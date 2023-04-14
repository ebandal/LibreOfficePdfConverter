package ebandal.context.LibreOffice;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

import ebandal.context.conv.ConvertContext.ProcessStreamCatcher;

@Component
public class SOfficeProcessManager {
    private static final Logger log = LoggerFactory.getLogger(SOfficeProcessManager.class);

    @Value("${conv.command.libreoffice}")
    private String libreoffice;

    @Autowired
    private ConfigurableEnvironment env;

    @PostConstruct
    public void init() {
        
        List<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        StringBuffer osEnv = new StringBuffer();
        
        if (activeProfiles.contains("linux")) {
            osEnv.append("linux");
        } else if (activeProfiles.contains("wsl")) {
            osEnv.append("wsl");
        }
        
        if (osEnv.equals("linux") || osEnv.equals("wsl")) {
            killOfficeProcess(osEnv.toString());
            List<Thread> thList = IntStream.range(10001, 10003)
                                                .mapToObj(port -> new Thread(new SOfficeProcessThread(osEnv.toString(), libreoffice, port)))
                                                .collect(Collectors.toList());
            thList.stream().forEach(th -> th.start());
        }
    }

    
    public void killOfficeProcess(String osEnv) {
        
        List<String> commandWithArgs = new ArrayList<String>();
        
        if (osEnv.equals("linux")) {
            // bash -c 'mkdir -p /PDFCONV/disk/input/ & cd /PDFCONV/disk/input/ & soffice --headless --convert-to pdf:writer_pdf_Export /PDFCONV/disk/input/변환할파일.pptx'
            commandWithArgs.add("bash");        commandWithArgs.add("-c");
            commandWithArgs.add("pkill -x soffice.bin");
        } else if (osEnv.equals("wsl")) {
            commandWithArgs.add("wsl");
            commandWithArgs.add("--user");  commandWithArgs.add("root");
            commandWithArgs.add("pkill");   commandWithArgs.add("-x");  commandWithArgs.add("soffice.bin");
        }
        
        ProcessBuilder builder = new ProcessBuilder();
        builder.redirectErrorStream(true);
        builder = builder.command(commandWithArgs);
        if (osEnv.equals("linux")) {
            builder = builder.directory(new File("."));
        }

        log.info("LibreOffice command line: " + commandWithArgs.stream().collect(Collectors.joining(" ")));

        try {
            Process process = builder.start();
            Thread th = new Thread(new ProcessStreamCatcher(process.getInputStream()));
            th.start();
            process.waitFor();
            th.join();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

}
