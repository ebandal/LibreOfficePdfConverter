package ebandal.context.LibreOffice;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

import ebandal.context.ConvertService.ProgressCallback;
import ebandal.context.conv.ConvertContext;
import ebandal.context.conv.ConvertContext.MatchCallback;
import ebandal.context.conv.ConvertContext.ParamCallback;
import ebandal.context.conv.ConvertContext.ProcessStreamCatcher;
import ebandal.controller.ControllerStopException;
import ebandal.controller.ErrCode;
import ebandal.util.WslEnvSetup;

@Component("BIN")
public class PDFServiceSOfficeImpl implements PDFService {
    private static final Logger log = LoggerFactory.getLogger(PDFServiceSOfficeImpl.class);
    private static String winCodePage = null;

    @Autowired
    private ConfigurableEnvironment env;
    
    private void makeGeneratePdfArgs(ParamCallback callback, String workingDir, String documentPath) {
        callback.addCommand();
        
        String extension = documentPath.replaceAll(".*\\.(xls|xlsx|ppt|pptx|doc|docx|hwp|hwpx)", "$1");
        String inFilter = "";
        String outFilter = "";
        switch(extension) {
        case "docx":
        case "doc":
                outFilter = "pdf:writer_pdf_Export";
                break;
        case "xlsx":
        case "xls":
                outFilter = "pdf:calc_pdf_Export";
                break;
        case "pptx":
        case "ppt":
                outFilter = "pdf:impress_pdf_Export";
                break;
        case "hwp":
        case "hwpx":
                inFilter = "\"Hwp2002_File\"";
                outFilter = "pdf:writer_pdf_Export";
                break;
        default:
                log.error("unsupported extendsion");
                throw new ControllerStopException(ErrCode.UNDEFINED);
        }
        
        callback.add("--headless");
        if (!inFilter.isEmpty()) {
            callback.add("--infilter="+inFilter);
        }
        callback.add("--convert-to");
        callback.add(outFilter);
        
        String convertedPath = callback.convertedPath(documentPath);
        callback.add("\""+convertedPath+"\"");
    }

    @Override
    public String getMode() {
        return "BIN";
    }
    
    @Override
    public String convert(ConvertContext context, String documentPath, String outputPath, ProgressCallback prgrssCB)
                                            throws IOException, InterruptedException, ControllerStopException {
        
        List<String> commandWithArgs = new ArrayList<String>();
        String workingDir = outputPath;

        List<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        
        if (activeProfiles.contains("wsl")) {
            workingDir = WslEnvSetup.convertWslPath(workingDir);
            String[] cmdToken = context.sofficePath.split(" ");
            commandWithArgs.add("wsl");
            commandWithArgs.add("--user");  commandWithArgs.add("root"); 
            commandWithArgs.add("mkdir");  commandWithArgs.add("-p");     commandWithArgs.add(workingDir); 
            commandWithArgs.add("&&");     commandWithArgs.add("cd");     commandWithArgs.add(workingDir);
            commandWithArgs.add("&&");
            
            ParamCallback callback = new ParamCallback() {
                public void add(String arg) {
                    commandWithArgs.add(arg);
                }
                public void addCommand() {
                    commandWithArgs.add(context.sofficePath);
                }
                public String convertedPath(String path) {
                    return WslEnvSetup.convertWslPath(path);
                }
            };
            makeGeneratePdfArgs(callback, workingDir, documentPath);
            
        } else if (activeProfiles.contains("linux")==true) {
            // bash -c 'soffice --headless --convert-to pdf:writer_pdf_Export /E-BOOK/HTML5CONV/disk/input/2021_1H_솔루션데이_최종.pptx'
            commandWithArgs.add("bash");        commandWithArgs.add("-c");
            StringBuffer argSb = new StringBuffer();
            
            ParamCallback callback = new ParamCallback() {
                public void add(String arg) {
                    argSb.append(arg + " ");
                }
                public void addCommand() {
                    argSb.append(context.sofficePath + " ");
                }
                public String convertedPath(String path) {
                    return path;
                }
            };
            makeGeneratePdfArgs(callback, workingDir, documentPath);
            commandWithArgs.add(argSb.toString());
            
        } else if (activeProfiles.contains("windows")==true) {
            if (winCodePage == null) {
                winCodePage = getCodePage();
            }
            
            // "C:\Program Files\LibreOffice\program\soffice.exe" --headless --infilter="Hwp2002_Reader" --convert-to pdf:writer_pdf_Export "C:\PDFCONV\disk\input\모집요강.hwp"
            StringBuffer argSb = new StringBuffer();

            ParamCallback callback = new ParamCallback() {
                public void add(String arg) {
                    commandWithArgs.add(arg);
                }
                public void addCommand() {
                    commandWithArgs.add(context.sofficePath);
                }
                public String convertedPath(String path) {
                    return path;
                }
            };
            makeGeneratePdfArgs(callback, workingDir, documentPath);
            commandWithArgs.add(argSb.toString());
        }
        
        log.debug("LibreOffice command line: " + commandWithArgs.stream().collect(Collectors.joining(" ")));
        
        ProcessBuilder builder = new ProcessBuilder();
        builder.redirectErrorStream(true);
        builder = builder.command(commandWithArgs);
        if (activeProfiles.contains("linux")) {
            builder = builder.directory(Paths.get(workingDir).toFile());
        } else if (activeProfiles.contains("windows")) {
            builder = builder.directory(Paths.get(workingDir).toFile());
        }
        
        long elapseStart = System.currentTimeMillis();
        log.info("BIN Conerting Document to PDF starting");

        Process process = builder.start();
        
        List<String> callbackList = new ArrayList<String>();
        MatchCallback mc = new MatchCallback() {
            @Override
            public void onReceive(String line) {
                if (line.startsWith("convert")) {
                    String res = line.replaceAll("convert [^\\s]+ -> ([^\\s]+) .*", "$1");
                    callbackList.add(res);
                } 
                if (line.matches("HWP converting \\d+ \\/ \\d+")) {
                    log.debug(line);
                }
                else if (line.matches("(E|e)rror.*")) {
                    prgrssCB.lastErrMsg = line;
                }
            }
        };
        ProcessStreamCatcher psc = null; 
        if (activeProfiles.contains("windows")) {
            psc = new ProcessStreamCatcher(process.getInputStream(), winCodePage, mc);
        } else {
            psc = new ProcessStreamCatcher(process.getInputStream(), mc);
        }
        Thread t1 = new Thread(psc);
        t1.start();
        boolean exitCode = process.waitFor(context.timeoutSec, TimeUnit.SECONDS);
        
        long elapseEnd = System.currentTimeMillis();
        log.info("BIN Conerting Document to PDF completed: " + (elapseEnd-elapseStart)/1000 + " seconds");

        if (exitCode==false) {
            if (activeProfiles.contains("wsl") || activeProfiles.contains("linux")) {
                t1.interrupt();
                Process subProc = process.destroyForcibly();
                commandWithArgs.clear();
                if (activeProfiles.contains("wsl")) {
                    commandWithArgs.add("wsl");     commandWithArgs.add("--user");  commandWithArgs.add("root"); 
                }
                commandWithArgs.add("pkill");   commandWithArgs.add("-9");      commandWithArgs.add("-o");      commandWithArgs.add("soffice");
                builder = builder.command(commandWithArgs);
                subProc = builder.start();
                int exit = subProc.waitFor();
                log.info("SubProcess exit with "+exit);
                throw new ControllerStopException(ErrCode.SOFFICE_PROCESS_TERMINATED_ABNORMALLY);
            }
        }
        t1.join();
        
        String pdfPath = null;
        if (callbackList.size()==1) {
            pdfPath = callbackList.get(0);
            if (activeProfiles.contains("wsl")) {
                pdfPath = WslEnvSetup.pathWsl2Windows(pdfPath);
            } else if (activeProfiles.contains("windows")) {
                pdfPath = WslEnvSetup.pathWin2Windows(pdfPath);
            }
        }

        return pdfPath;
    }

    private String getCodePage() {
        String charset = "";
        List<String> commandWithArgs = new ArrayList<String>();
        commandWithArgs.add("cmd");     commandWithArgs.add("/c");      commandWithArgs.add("chcp");
        
        ProcessBuilder builder = new ProcessBuilder();
        builder.redirectErrorStream(true);
        builder = builder.command(commandWithArgs);

        try {
            Process process = builder.start();
        
            StringBuffer sb = new StringBuffer();
            Thread t1 = new Thread(new ProcessStreamCatcher(process.getInputStream(), new MatchCallback() {
                @Override
                public void onReceive(String line) {
                    sb.append(line.replaceAll(".*: (.+)$", "$1"));
                }
            }));
            t1.start();
            boolean exitCode = process.waitFor(1000, TimeUnit.SECONDS);
            t1.join();
            switch(sb.toString()) {
            case "037":                     // 037 IBM037  IBM EBCDIC US-Canada
                charset = "Cp037"; break;
            case "437":                     // 437 IBM437  OEM 미국
                charset = "Cp437"; break;
            case "500":                     // 500 IBM500  IBM EBCDIC 국제
                charset = "Cp500"; break;
            case "708":                     // 708 ASMO-708    아랍어 (ASMO 708)
                charset = "ISO8859_6"; break;
            case "709":                     // 709     아랍어 (ASMO-449 +, BCON V4)
            case "710":                     // 710     아랍어-투명 아랍어
            case "720":                     // 720 DOS-720 아랍어 (투명 한 ASMO); 아랍어 (DOS)
                charset = ""; break;
            case "737":                     // 737 ibm737  OEM 그리스어 (이전의 437G); 그리스어 (DOS)
                charset = "Cp737"; break;
            case "775":                     // 775 ibm775  OEM 발트어; 발트어 (DOS)
                charset = "Cp775"; break;
            case "850":                     // 850 ibm850  OEM 다국어 라틴어 1; 서유럽어 (DOS)
                charset = "Cp850"; break;
            case "852":                     // 852 ibm852  OEM 라틴어 2; 중앙 유럽어 (DOS)
                charset = "Cp852"; break;
            case "855":                     // 855 IBM855  OEM 키릴 자모 (주로 러시아어)
                charset = "Cp855"; break;
            case "857":                     // 857 ibm857  OEM 터키어; 터키어 (DOS)
                charset = "Cp857"; break;
            case "858":                     // 858 IBM00858    OEM 다국어 라틴어 1 + 유로 기호
                charset = "Cp858"; break;
            case "860":                     // 860 IBM860  OEM 포르투갈어; 포르투갈어 (DOS)
                charset = "Cp860"; break;
            case "861":                     // 861 ibm861  OEM 아이슬란드어; 아이슬란드어 (DOS)
                charset = "Cp861"; break;
            case "862":                     // 862 DOS-862 OEM 히브리어; 히브리어 (DOS)
                charset = "Cp862"; break;
            case "863":                     // 863 IBM863  OEM 프랑스어 (캐나다) 프랑스어 (캐나다) (DOS)
                charset = "Cp863"; break;
            case "864":                     // 864 IBM864  OEM 아랍어; 아랍어 (864)
                charset = "Cp864"; break;
            case "865":                     // 865 IBM865  OEM 북유럽어; 북유럽어 (DOS)
                charset = "Cp865"; break;
            case "866":                     // 866 cp866   OEM 러시아어; 키릴 자모 (DOS)
                charset = "Cp866"; break;
            case "869":                     // 869 ibm869  OEM 최신 그리스어 그리스어, 현대 (DOS)
                charset = "Cp869"; break;
            case "870":                     // 870 IBM870  IBM EBCDIC 다국어/ROECE (라틴어 2); IBM EBCDIC 다국어 라틴어 2
                charset = "Cp870"; break;
            case "874":                     // 874 windows-874 태국어 (Windows)
                charset = "MS874"; break;
            case "875":                     // 875 cp875   IBM EBCDIC 현대 그리스어
                charset = "Cp875"; break;
            case "932":                     // 932 shift _ jis ANSI/OEM 일본어; 일본어 (shift-jis)
                charset = "SJIS"; break;
            case "936":                     // 936 gb2312  ANSI/OEM 중국어 간체 (중국, 싱가포르); 중국어 간체 (GB2312)
                charset = "MS936"; break;
            case "949":                     // 949 ks _ c _ 5601-1987  ANSI/OEM 한국어 (통합 한글 코드)
                charset = "Cp949"; break;
            case "950":                     // 950 번체  ANSI/OEM 중국어 번체 (대만) 홍콩 특별 행정구, PRC); 중국어 번체 (Big5)
                charset = "Cp950"; break;
            case "1026":                    // 1026    IBM1026 IBM EBCDIC 터키어 (라틴어 5)
                charset = "Cp1026"; break;
            case "1047":                    // 1047    IBM01047    IBM EBCDIC 라틴어 1/Open System
                charset = "Cp1047"; break;
            case "1140":                    // 1140    IBM01140    IBM EBCDIC US-Canada(037 + 유럽 기호); IBM EBCDIC(미국-캐나다-유럽)
                charset = "Cp1140"; break;
            case "1141":                    // 1141    IBM01141    IBM EBCDIC 독일(20273 + 유럽 기호); IBM EBCDIC(독일-유럽)
                charset = "Cp1141"; break;
            case "1142":                    // 1142    IBM01142    IBM EBCDIC Denmark-Norway(20277 + 유럽 기호); IBM EBCDIC(덴마크-노르웨이-유럽)
                charset = "Cp1142"; break;
            case "1143":                    // 1143    IBM01143    IBM EBCDIC Finland-Sweden(20278 + 유럽 기호); IBM EBCDIC(핀란드-스웨덴-유럽)
                charset = "Cp1143"; break;
            case "1144":                    // 1144    IBM01144    IBM EBCDIC 이탈리아어(20280 + 유럽 기호); IBM EBCDIC(이탈리아-유럽)
                charset = "Cp1144"; break;
            case "1145":                    // 1145    IBM01145    IBM EBCDIC Latin America-Spain(20284 + 유럽 기호); IBM EBCDIC(스페인-유럽)
                charset = "Cp1145"; break;
            case "1146":                    // 1146    IBM01146    IBM EBCDIC United United Kingdom(20285 + Euro symbol); IBM EBCDIC(영국-유럽)
                charset = "Cp1146"; break;
            case "1147":                    // 1147    IBM01147    IBM EBCDIC France(20297 + 유럽 기호); IBM EBCDIC(프랑스-유럽)
                charset = "Cp1147"; break;
            case "1148":                    // 1148    IBM01148    IBM EBCDIC International(500 + 유럽 기호); IBM EBCDIC(국제-유럽)
                charset = "Cp1148"; break;
            case "1149":                    // 1149    IBM01149    IBM EBCDIC 표시(20871 + 유럽 기호); IBM EBCDIC(네덜란드어-유럽)
                charset = "Cp1149"; break;
            case "1200":                    // 1200    u t f-16    유니코드 UTF-16, little endian 바이트 순서(ISO 10646의 BMP); 관리되는 애플리케이션에만 사용 가능
                charset = "UTF-16LE"; break;
            case "1201":                    // 1201    unicodeFFFE 유니코드 UTF-16, big endian 바이트 순서; 관리되는 애플리케이션에만 사용 가능
                charset = "UTF-16BE"; break;
            case "1250":                    // 1250    windows-1250    ANSI 중앙 유럽; 중앙 유럽(Windows)
                charset = "Cp1250"; break;
            case "1251":                    // 1251    windows-1251    ANSI 키릴자루; 키릴자열(Windows)
                charset = "Cp1251"; break;
            case "1252":                    // 1252    windows-1252    ANSI 라틴어 1; 서유럽어(Windows)
                charset = "Cp1252"; break;
            case "1253":                    // 1253    windows-1253    ANSI 그리스어; 그리스어(Windows)
                charset = "Cp1253"; break;
            case "1254":                    // 1254    windows-1254    ANSI 터키어; 터키어(Windows)
                charset = "Cp1254"; break;
            case "1255":                    // 1255    windows-1255    ANSI 히브리어; 히브리어(Windows)
                charset = "Cp1255"; break;
            case "1256":                    // 1256    windows-1256    ANSI 아랍어; 아랍어(Windows)
                charset = "Cp1256"; break;
            case "1257":                    // 1257    windows-1257    ANSI 히로인; Windows( )
                charset = "Cp1257"; break;
            case "1258":                    // 1258    windows-1258    ANSI/OEM 대만어; 현지어(Windows)
                charset = "Cp1258"; break;
            case "1361":                    // 1361    조합  한국어 (조합)
                charset = "x-Johab"; break;
            case "10000":                   // 10000   용   MAC 로마; 서유럽어(Mac)
            case "10001":                   // 10001   x-mac-일본어   일본어 (Mac)
            case "10002":                   // 10002   x-mac-chinesetrad   MAC 중국어 번체(Big5); 중국어 번체(Mac)
            case "10003":                   // 10003   x-mac-한국어   한국어(Mac)
            case "10004":                   // 10004   x-mac-아랍어   아랍어 (Mac)
            case "10005":                   // 10005   x-mac-히브리어  히브리어 (Mac)
            case "10006":                   // 10006   x-mac-그리스어  그리스어 (Mac)
            case "10007":                   // 10007   x-y-키릴 자모   키릴 자모 (Mac)
            case "10008":                   // 10008   chinesesimp MAC 중국어 간체(GB 2312); 중국어 간체(Mac)
            case "10010":                   // 10010   x-y-루마니아어   루마니아어 (Mac)
            case "10017":                   // 10017   x-mac-우크라이나어    우크라이나어 (Mac)
            case "10021":                   // 10021   x-mac – 태국어 태국어 (Mac)
            case "10029":                   // 10029   x-y-ce  MAC 라틴어 2; 중앙 유럽어 (Mac)
            case "10079":                   // 10079   x-mac-아이슬란드어    아이슬란드어 (Mac)
            case "10081":                   // 10081   x-y-터키어 터키어 (Mac)
            case "10082":                   // 10082   x-mac-크로아티아어    크로아티아어 (Mac)
            case "12000":                   // 12000   utf-32  유니코드 UTF-32, little endian 바이트 순서 관리 되는 응용 프로그램에만 사용 가능
            case "12001":                   // 12001   utf-32BE    유니코드 UTF-32, big endian 바이트 순서 관리 되는 응용 프로그램에만 사용 가능
            case "20000":                   // 20000   x-중국어 _ cn  CN 대만; 중국어 번체 (CN)
            case "20001":                   // 20001   x-cp20001   TCA 대만
            case "20002":                   // 20002   x _ 중국어-Eten    Eten 대만; 중국어 번체 (Eten)
            case "20003":                   // 20003   x-cp20003   IBM5550 대만
            case "20004":                   // 20004   x-cp20004   문자 다중 문자 (대만)
            case "20005":                   // 20005   x-cp20005   Wang 대만
            case "20105":                   // 20105   x-IA5   IA5 (IRV 국제 알파벳 번호) 5, 7 비트); 서유럽어 (IA5)
            case "20106":                   // 20106   x IA5-독일어   IA5 독일어 (7 비트)
            case "20107":                   // 20107   x IA5-스웨덴어  IA5 스웨덴어 (7 비트)
            case "20108":                   // 20108   x IA5-노르웨이어 IA5 노르웨이어 (7 비트)
            case "20127":                   // 20127   us-ascii    US-ASCII (7 비트)
            case "20261":                   // 20261   x-cp20261   T. 61
            case "20269":                   // 20269   x-cp20269   ISO 6937 Non-Spacing Accent
                charset = ""; break;
            case "20273":                   // 20273   IBM273  IBM EBCDIC 독일
                charset = "Cp273"; break;
            case "20277":                   // 20277   IBM277  IBM EBCDIC Denmark-Norway
                charset = "Cp277"; break;
            case "20278":                   // 20278   IBM278  IBM EBCDIC Finland-Sweden
                charset = "Cp278"; break;
            case "20280":                   // 20280   IBM280  IBM EBCDIC 이탈리아
                charset = "Cp280"; break;
            case "20284":                   // 20284   IBM284  IBM EBCDIC 라틴어 America-Spain
                charset = "Cp284"; break;
            case "20285":                   // 20285   IBM285  IBM EBCDIC 영국
                charset = "Cp285"; break;
            case "20290":                   // 20290   IBM290  IBM EBCDIC 일본어 가타카나 확장
                charset = "Cp290"; break;
            case "20297":                   // 20297   IBM297  IBM EBCDIC 프랑스
                charset = "Cp2970"; break;
            case "20420":                   // 20420   IBM420  IBM EBCDIC 아랍어
                charset = "Cp420"; break;
            case "20423":                   // 20423   IBM423  IBM EBCDIC 그리스어
                charset = "Cp423"; break;
            case "20424":                   // 20424   IBM424  IBM EBCDIC 히브리어
                charset = "Cp424"; break;
            case "20833":                   // 20833   x-EBCDIC-KoreanExtended IBM EBCDIC 한국어 확장
                charset = "Cp1364"; break;
            case "20838":                   // 20838   IBM-태국어 IBM EBCDIC 태국어
            case "20866":                   // 20866   (koi8-r 러시아어 ((KOI8-R); 키릴 자모 ((KOI8-R)
            case "20871":                   // 20871   IBM871  IBM EBCDIC 아이슬란드어
            case "20880":                   // 20880   IBM880  IBM EBCDIC 키릴 자모 러시아어
            case "20905":                   // 20905   IBM905  IBM EBCDIC 터키어
            case "20924":                   // 20924   IBM00924    IBM EBCDIC 라틴어 1/Open System (1047 + 유로 기호)
                charset = ""; break;
            case "20932":                   // 20932   EUC-JP  일본어 (JIS 0208-1990 및 0212-1990)
                charset = "EUC_JP"; break;
            case "20936":                   // 20936   x-cp20936   중국어 간체 (GB2312); 중국어 간체 (GB2312-80)
                charset = "EUC_CN"; break;
            case "20949":                   // 20949   x-cp20949   한국어 Korean-wansung-unicode
                charset = ""; break;
            case "21025":                   // 21025   cp1025  IBM EBCDIC 키릴 자모 Serbian-Bulgarian
                charset = "Cp1025"; break;
            case "21027":                   // 21027       mapi
                charset = ""; break;
            case "21866":                   // 21866   (koi8-u 우크라이나어 ((KOI8-U); 키릴 자모 ((KOI8-U)
                charset = "KOI8_U"; break;
            case "28591":                   // 28591   iso-8859-1  ISO 8859-1 라틴어 1; 서유럽어 (ISO)
                charset = "ISO8859_1"; break;
            case "28592":                   // 28592   iso-8859-2  ISO 8859-2 중앙 유럽어 중앙 유럽어 (ISO)
                charset = "ISO8859_2"; break;
            case "28593":                   // 28593   iso-8859-3  ISO 8859-3 라틴어 3
                charset = "ISO8859_3"; break;
            case "28594":                   // 28594   iso-8859-4  ISO 8859-4 발트어
                charset = "ISO8859_4"; break;
            case "28595":                   // 28595   iso-8859-5  ISO 8859-5 키릴 자모
                charset = "ISO8859_5"; break;
            case "28596":                   // 28596   iso-8859-6  ISO 8859-6 아랍어
                charset = "ISO8859_6"; break;
            case "28597":                   // 28597   iso-8859-7  ISO 8859-7 그리스어
                charset = "ISO8859_7"; break;
            case "28598":                   // 28598   iso-8859-8  ISO 8859-8 히브리어; 히브리어 (ISO-시각적 개체)
                charset = "ISO8859_8"; break;
            case "28599":                   // 28599   iso-8859-9  ISO 8859-9 터키어
                charset = "ISO8859_9"; break;
            case "28603":                   // 28603   iso-8859-13 ISO 8859-13 에스토니아어
                charset = "ISO8859_13"; break;
            case "28605":                   // 28605   iso-8859-15 ISO 8859-15 라틴어 9
                charset = "ISO8859_15"; break;
            case "29001":                   // 29001   x-유럽어   유럽어 3
                charset = ""; break;
            case "38598":                   // 38598   iso-8859-8-i    ISO 8859-8 히브리어; 히브리어 (ISO-논리적)
                charset = "ISO8859_8"; break;
            case "50220":                   // 50220   iso-2022-jp ISO 2022 일본어 (반자 가타카나 없음) 일본어 (JIS)
            case "50221":                   // 50221   csISO2022JP ISO 2022 일본어 반자 가타카나; 일본어 (JIS-1 바이트가 나)
                charset = "ISO2022JP2"; break;
            case "50222":                   // 50222   iso-2022-jp ISO 2022 일본어 JIS X 0201-1989; 일본어 (JIS-1 바이트가 나-a s i/SI)
                charset = "ISO2022JP"; break;
            case "50225":                   // 50225   iso-2022-kr ISO 2022 한국어
                charset = "ISO2022KR"; break;
            case "50227":                   // 50227   x-cp50227   ISO 2022 중국어 간체; 중국어 간체 (ISO 2022)
            case "50229":                   // 50229       ISO 2022 중국어 번체
                charset = "ISO2022CN"; break;
            case "50930":                   // 50930       EBCDIC 일본어 (가타카나) 확장
                charset = "Cp290"; break;
            case "50931":                   // 50931       EBCDIC US-Canada 및 일본어
            case "50933":                   // 50933       EBCDIC 한국어 확장 및 한국어
            case "50935":                   // 50935       EBCDIC 중국어 간체 및 중국어 간체
            case "50936":                   // 50936       EBCDIC 중국어 간체
            case "50937":                   // 50937       EBCDIC US-Canada 및 중국어 번체
            case "50939":                   // 50939       EBCDIC 일본어 (라틴어) 확장 및 일본어
                charset = ""; break;
            case "51932":                   // 51932   euc-jp  EUC 일본어
                charset = "EUC_JP"; break;
            case "51936":                   // 51936   EUC-CN  EUC 중국어 간체; 중국어 간체 (EUC)
                charset = "EUC_CN"; break;
            case "51949":                   // 51949   euc-kr  EUC 한국어
                charset = "EUC_KR"; break;
            case "51950":                   // 51950       EUC 중국어 번체
            case "52936":                   // 52936   hz-gb-2312  HZ-GB2312 중국어 간체; 중국어 간체 (HZ)
            case "54936":                   // 54936   GB18030 Windows XP 이상: GB18030 중국어 간체 (4 바이트); 중국어 간체 (GB18030)
            case "57002":                   // 57002   x-iscii-de  ISCII 데바나가리어
            case "57003":                   // 57003   x-iscii-be  ISCII 벵골어
            case "57004":                   // 57004   x-iscii-ta  ISCII 타밀어
            case "57005":                   // 57005   x-iscii-te  ISCII 텔루구어
            case "57006":                   // 57006   x-iscii-as  ISCII 아샘어
            case "57007":                   // 57007   x-iscii-또는  ISCII 오리야어
            case "57008":                   // 57008   x-y-카   ISCII 카나다어
            case "57009":                   // 57009   x-iscii-ma  ISCII 말라얄람어
            case "57010":                   // 57010   gu  ISCII 구자라트어
            case "57011":                   // 57011   x-iscii-pa  ISCII 펀잡어
                charset = ""; break;
            case "65000":                   // 65000   u t f-7 유니코드 (UTF-7)
                charset = "UTF-7"; break;
            case "65001":                   // 65001   utf-8   유니코드(UTF-8)
                charset = "UTF-8"; break;
            }
            charset = sb.toString();
            
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        
        return charset;
    }
}
