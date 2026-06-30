package com.lfernandes.modusweb.services;

import com.lfernandes.modusweb.exceptions.FileStorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    private final Path uploadRoot;

    public FileStorageService(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        initDirectories();
    }

    private void initDirectories() {
        try {
            Files.createDirectories(uploadRoot.resolve("previews"));
            Files.createDirectories(uploadRoot.resolve("templates"));
            Files.createDirectories(uploadRoot.resolve("avatars"));
            log.info("Upload root: {}", uploadRoot);
        } catch (IOException e) {
            throw new FileStorageException("Não foi possível criar os diretórios de upload.", e);
        }
    }

    public String storePreviewImage(MultipartFile file) {
        return store(file, "previews");
    }

    public String storeTemplateFile(MultipartFile file) {
        return store(file, "templates");
    }

    public String storeAvatar(MultipartFile file) {
        return store(file, "avatars");
    }

    public Resource loadAsResource(String relativePath) {
        Path file = resolveSecure(relativePath);
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new FileStorageException("Arquivo não encontrado: " + relativePath);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new FileStorageException("Caminho inválido: " + relativePath, e);
        }
    }

    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        try {
            Files.deleteIfExists(resolveSecure(relativePath));
        } catch (IOException | FileStorageException e) {
            log.warn("Falha ao deletar arquivo [{}]: {}", relativePath, e.getMessage());
        }
    }

    public Path resolveSecure(String relativePath) {
        Path resolved = uploadRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new FileStorageException("Path Traversal detectado: " + relativePath);
        }
        return resolved;
    }

    public String getPublicUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return "/assets/images/placeholder.png";
        return "/uploads/" + relativePath;
    }

    private String store(MultipartFile file, String subdir) {
        String extension = extractExtension(file.getOriginalFilename());
        String fileName  = UUID.randomUUID() + "." + extension;
        Path   target    = uploadRoot.resolve(subdir).resolve(fileName);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Arquivo salvo: {}", target);
        } catch (IOException e) {
            throw new FileStorageException("Falha ao salvar arquivo: " + fileName, e);
        }
        return subdir + "/" + fileName;
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "bin";
        return fileName.substring(fileName.lastIndexOf('.') + 1)
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");
    }
}
