package ebandal.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ebandal.context.ConvertService;
import ebandal.context.ConvertService.ProgressCallback;

@RestController
public class FileUploadController {
    private static Logger log = LoggerFactory.getLogger(FileUploadController.class);
    private Path rootLocation;

    @Value("${conv.input.directory}")
    private String uploadPath;

    @Value("${conv.overwrite}")
    private String overwrite;

    @Autowired
    private ConvertService convertService;

    @PostConstruct
    public void init() {
        rootLocation = Paths.get(uploadPath);
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new StorageException("Could not initialize storage", e);
        }
    }

    @RequestMapping(value = "/fileUpload", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "text/html; charset=UTF-8")
    @ResponseBody
    public ResponseEntity<InputStreamResource> handleFileUpload(@RequestParam("file") List<MultipartFile> file) {

        InputStreamResource resource;

        MultipartFile lastFile = file.get(file.size() - 1);

        try {
            Path documentPath = store(lastFile);
            log.info("Uploading file saved to " + documentPath.toString());

            ProgressCallback prgrssCB = new ProgressCallback();
            if (convertService.convert(documentPath, prgrssCB)) {
                log.info("key is [" + prgrssCB.key + "]");
                resource = new InputStreamResource(new ByteArrayInputStream(prgrssCB.key.getBytes()));
                return ResponseEntity.ok().body(resource);
            } else {
                log.info("errMsg", prgrssCB.lastErrMsg);
                resource = new InputStreamResource(new ByteArrayInputStream(prgrssCB.lastErrMsg.getBytes()));
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resource);
            }
        } catch (ConvertSkipException e) {
            // 이미 PDF 파일이 있는 경우, 변환폴더가 있다고 가정하여 해당 key값을 리턴한다. 동일 파일명으로 다시 변환하지 않는다.
            String keyName = substituteFileName(lastFile.getOriginalFilename());
            keyName = keyName.replaceAll("\\.[^\\.]+$", "");
            resource = new InputStreamResource(new ByteArrayInputStream(keyName.getBytes()));
            return ResponseEntity.ok().body(resource);
        } catch (ControllerStopException | IOException | InterruptedException e) {
            log.error(e.getMessage());

            resource = new InputStreamResource(new ByteArrayInputStream(e.getMessage().getBytes()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resource);
        }
    }

    private String substituteFileName(String fileName) {
        return fileName.replaceAll(" ", "_").replaceAll("\\[", "(").replaceAll("\\]", ")").replaceAll("'", "");
    }

    public Path store(MultipartFile file) throws ConvertSkipException {
        Path destinationFile = null;
        try {
            if (file.isEmpty()) {
                throw new StorageException("Failed to store empty file.");
            }
            // 파일명에 공백을 허락하지 않도록 한다. 공백은 '_' 문자로 치환, '['은 '('로 치환, ']'는 ')'로 치환
            destinationFile = this.rootLocation.resolve(Paths.get(substituteFileName(file.getOriginalFilename())))
                                               .normalize().toAbsolutePath();
            if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
                throw new StorageException("Cannot store file outside current directory.");
            }
            try (InputStream inputStream = file.getInputStream()) {
                long bytesNum = 0;
                if (overwrite.equals("true")) {
                    bytesNum = Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    bytesNum = Files.copy(inputStream, destinationFile);
                }
                log.info("Bytes copied = " + bytesNum);
            }
        } catch (FileAlreadyExistsException e) {
            // 이미 변환된 파일이 있으므로 정상 처리...
            log.info("File already exists : " + file.getOriginalFilename());
            throw new ConvertSkipException(e.getLocalizedMessage());
        } catch (IOException e) {
            throw new StorageException("Failed to store file.", e);
        }
        return destinationFile;
    }

    public Stream<Path> loadAll() {
        try {
            return Files.walk(this.rootLocation, 1).filter(path -> !path.equals(this.rootLocation))
                        .map(this.rootLocation::relativize);
        } catch (IOException e) {
            throw new StorageException("Failed to read stored files", e);
        }
    }

    public Path load(String filename) {
        return rootLocation.resolve(filename);
    }

    public Resource loadAsResource(String filename) {
        try {
            Path file = load(filename);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new StorageFileNotFoundException("Could not read file: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new StorageFileNotFoundException("Could not read file: " + filename, e);
        }
    }

    public void deleteAll() {
        FileSystemUtils.deleteRecursively(rootLocation.toFile());
    }
}
