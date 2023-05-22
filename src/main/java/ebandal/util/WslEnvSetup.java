package ebandal.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WslEnvSetup {
	private static final Logger log = LoggerFactory.getLogger(WslEnvSetup.class);
	
	public static String convertWslPath(String pdfPath) {
		StringBuilder wslPath = new StringBuilder();
		wslPath.append("/mnt");
		Path path = Paths.get(pdfPath);
		wslPath.append("/"+path.getRoot().toString().replaceAll("\\\\","").replaceAll(":","").toLowerCase());
		for (int i=0; i < path.getNameCount(); i++) {
			wslPath.append("/"+path.getName(i).toString());
		}
		return wslPath.toString();
	}
	
	public static String pathWsl2Windows(String wslPath) {
		StringBuilder windowPath = new StringBuilder();
		Path path = Paths.get(wslPath);
		for (int i=0; i < path.getNameCount(); i++) {
			if (i==0 && path.getName(i).toString().equals("mnt"))	continue;
			if (i==1) {
				windowPath.append(path.getName(i).toString().toUpperCase()+":\\");
			} else {
				windowPath.append("\\"+path.getName(i).toString());
			}
		}
		return windowPath.toString();
	}

	public static String pathWin2Windows(String winPath) {
        String retStr = winPath;
        try {
	        retStr = winPath.replaceAll("\u20a9", "\\\\");
	    } catch (PatternSyntaxException e) {
	        e.printStackTrace();
	    }
	    return retStr;
    }

}
