package com.bqpvalidateexcel.storage.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Quản lý và chuẩn hóa các đường dẫn thư mục lưu trữ hệ thống BQP theo tiêu chuẩn:
 * %LOCALAPPDATA%\BQP\QuanLyCheDo\
 *   ├── data\bqp-data.db
 *   ├── imports\
 *   ├── backups\
 *   └── logs\
 */
@Component
public class DataDirectoryResolver {

    private final StorageProperties properties;
    private Path baseDirectory;
    private Path dataDirectory;
    private Path importsDirectory;
    private Path backupsDirectory;
    private Path logsDirectory;
    private Path databaseFile;

    public DataDirectoryResolver(StorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        resolveDirectories();
    }

    private synchronized void resolveDirectories() {
        if (baseDirectory != null) return;

        String configBase = properties.getBaseDir();
        if (configBase != null && !configBase.trim().isEmpty()) {
            baseDirectory = Paths.get(configBase.trim()).toAbsolutePath().normalize();
        } else {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.trim().isEmpty()) {
                baseDirectory = Paths.get(localAppData, "BQP", "QuanLyCheDo").toAbsolutePath().normalize();
            } else {
                String userHome = System.getProperty("user.home", ".");
                baseDirectory = Paths.get(userHome, ".bqp", "QuanLyCheDo").toAbsolutePath().normalize();
            }
        }

        dataDirectory = baseDirectory.resolve("data");
        importsDirectory = baseDirectory.resolve("imports");
        backupsDirectory = baseDirectory.resolve("backups");
        logsDirectory = baseDirectory.resolve("logs");
        databaseFile = dataDirectory.resolve(properties.getDbFileName());

        createDirectoryIfNotExists(baseDirectory);
        createDirectoryIfNotExists(dataDirectory);
        createDirectoryIfNotExists(importsDirectory);
        createDirectoryIfNotExists(backupsDirectory);
        createDirectoryIfNotExists(logsDirectory);

        System.out.println("[BQP Storage] Thư mục dữ liệu: " + baseDirectory);
        System.out.println("[BQP Storage] File SQLite: " + databaseFile);
    }

    private void createDirectoryIfNotExists(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            System.err.println("[BQP Storage] Không thể tạo thư mục " + path + ": " + e.getMessage());
        }
    }

    public Path getBaseDirectory() {
        if (baseDirectory == null) resolveDirectories();
        return baseDirectory;
    }

    public Path getDataDirectory() {
        if (dataDirectory == null) resolveDirectories();
        return dataDirectory;
    }

    public Path getImportsDirectory() {
        if (importsDirectory == null) resolveDirectories();
        return importsDirectory;
    }

    public Path getBackupsDirectory() {
        if (backupsDirectory == null) resolveDirectories();
        return backupsDirectory;
    }

    public Path getLogsDirectory() {
        if (logsDirectory == null) resolveDirectories();
        return logsDirectory;
    }

    public Path getDatabaseFile() {
        if (databaseFile == null) resolveDirectories();
        return databaseFile;
    }
}
