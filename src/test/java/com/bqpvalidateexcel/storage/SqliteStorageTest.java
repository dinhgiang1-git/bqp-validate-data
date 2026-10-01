package com.bqpvalidateexcel.storage;

import com.bqpvalidateexcel.storage.model.PersonnelRecordModel;
import com.bqpvalidateexcel.storage.model.UnitModel;
import com.bqpvalidateexcel.storage.model.ValidationErrorModel;
import com.bqpvalidateexcel.storage.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
public class SqliteStorageTest {

    private static final String TEST_STORAGE_DIR = System.getProperty("java.io.tmpdir") + "/bqp-test-storage-" + UUID.randomUUID().toString().substring(0, 8);

    @org.springframework.test.context.DynamicPropertySource
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("bqp.storage.base-dir", () -> TEST_STORAGE_DIR);
        registry.add("bqp.lock.enabled", () -> "false");
    }

    private MockMvc mockMvc;

    @Autowired
    private com.bqpvalidateexcel.storage.controller.RecordController recordController;

    @Autowired
    private com.bqpvalidateexcel.storage.controller.RollupController rollupController;

    @Autowired
    private com.bqpvalidateexcel.storage.controller.UnitController unitController;

    @Autowired
    private UnitService unitService;

    @Autowired
    private RecordService recordService;

    @Autowired
    private RollupService rollupService;

    @Autowired
    private DatabaseIntegrityService integrityService;

    @Autowired
    private LocalStorageMigrationService migrationService;

    @Autowired
    private com.bqpvalidateexcel.storage.service.ImportService importService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Autowired
    private com.bqpvalidateexcel.storage.service.StorageMaintenanceLock maintenanceLock;

    @Autowired
    private com.bqpvalidateexcel.storage.repository.ImportRepository importRepository;

    @Autowired
    private com.bqpvalidateexcel.storage.controller.ImportController importController;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(recordController, rollupController, unitController, importController)
                .setControllerAdvice(new com.bqpvalidateexcel.storage.controller.GlobalExceptionHandler())
                .build();
        jdbcTemplate.update("DELETE FROM validation_errors");
        jdbcTemplate.update("DELETE FROM personnel_records");
        jdbcTemplate.update("DELETE FROM imports");
        jdbcTemplate.update("UPDATE units SET parent_id = NULL WHERE is_preset = 0");
        jdbcTemplate.update("DELETE FROM units WHERE is_preset = 0");
    }

    @Test
    void testIntegrityAndSchema() {
        Map<String, Object> integrity = integrityService.checkIntegrity();
        assertEquals("PASSED", integrity.get("integrityCheck"));
        assertEquals("PASSED", integrity.get("foreignKeyCheck"));

        Map<String, Object> status = integrityService.getStorageStatus();
        assertTrue((Boolean) status.get("initialized"));
    }

    @Test
    void testUnitHierarchyAndCyclePrevention() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String rootId = "root_" + uid;
        String qkId = "qk_" + uid;
        String fId = "f_" + uid;

        // Cấp 1: Cục Tài chính
        UnitModel ctc = unitService.createUnit(UnitModel.builder()
                .id(rootId)
                .name("Cục Tài chính BQP " + uid)
                .code("CTC_" + uid)
                .level(1)
                .build());
        assertNotNull(ctc);

        // Cấp 2: Quân khu
        UnitModel qk = unitService.createUnit(UnitModel.builder()
                .id(qkId)
                .name("Quân khu " + uid)
                .code("QK_" + uid)
                .level(2)
                .parentId(rootId)
                .build());
        assertNotNull(qk);

        // Cấp 3: Sư đoàn
        UnitModel f = unitService.createUnit(UnitModel.builder()
                .id(fId)
                .name("Sư đoàn " + uid)
                .code("F_" + uid)
                .level(3)
                .parentId(qkId)
                .build());
        assertNotNull(f);

        // Kiểm tra chống tạo chu trình vòng lặp (f không thể làm cha của qk)
        assertThrows(IllegalStateException.class, () -> {
            unitService.moveUnit(qkId, fId);
        });

        // Kiểm tra lấy cây
        List<UnitModel> tree = unitService.getUnitTree();
        assertFalse(tree.isEmpty());
    }

    @Test
    void testRecordsAndRollup() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String parentId = "parent_" + uid;
        String childId = "child_" + uid;

        // Tạo đơn vị test
        unitService.createUnit(UnitModel.builder()
                .id(parentId)
                .name("Quân khu thử nghiệm " + uid)
                .level(2)
                .build());

        unitService.createUnit(UnitModel.builder()
                .id(childId)
                .name("Trung đoàn thử nghiệm " + uid)
                .level(3)
                .parentId(parentId)
                .build());

        // Thêm 2 bản ghi: 1 ở cha, 1 ở con
        PersonnelRecordModel r1 = PersonnelRecordModel.builder()
                .id("rec_1_" + uid)
                .source("manual")
                .unitId(parentId)
                .fullName("Nguyễn Văn A " + uid)
                .sheetType("I.1")
                .categoryCode("SQ")
                .actualTotal(10000000.0)
                .calculatedTotal(10000000.0)
                .difference(0.0)
                .hasErrors(false)
                .build();

        PersonnelRecordModel r2 = PersonnelRecordModel.builder()
                .id("rec_2_" + uid)
                .source("manual")
                .unitId(childId)
                .fullName("Trần Văn B " + uid)
                .sheetType("I.2")
                .categoryCode("QNCN")
                .actualTotal(5000000.0)
                .calculatedTotal(5000000.0)
                .difference(0.0)
                .hasErrors(false)
                .errorDetails(List.of(
                        ValidationErrorModel.builder()
                                .columnNumber(5)
                                .errorCode("WARN_SALARY")
                                .message("Lương kiểm tra")
                                .build()
                ))
                .build();

        recordService.saveBatch(List.of(r1, r2));

        // Kiểm tra phân trang
        Map<String, Object> pageData = recordService.findPaged("manual", parentId, "branch", null, null, null, null, 0, 10);
        assertEquals(2, pageData.get("totalElements"));

        // Kiểm tra Rollup
        Map<String, Object> rollup = rollupService.calculateRollup(parentId, "branch");
        Map<String, Object> branchTotals = (Map<String, Object>) rollup.get("branchTotals");
        Map<String, Object> overall = (Map<String, Object>) branchTotals.get("overall");

        assertEquals(2, ((Number) overall.get("total_records")).intValue());
        assertEquals(15000000.0, ((Number) overall.get("total_actual")).doubleValue(), 0.01);
    }

    @Autowired
    private BackupService backupService;

    @Test
    void testStorageIsolation() {
        Map<String, Object> status = integrityService.getStorageStatus();
        String dbPath = (String) status.get("databasePath");
        assertNotNull(dbPath);
        // Database kiểm thử phải được cô lập trong thư mục tạm, tuyệt đối không trỏ vào DB thật
        assertTrue(dbPath.contains("bqp-test-storage") || dbPath.contains("Temp") || dbPath.contains("tmp"),
                "DB test phải nằm trong thư mục tạm cách ly: " + dbPath);
        assertFalse(dbPath.contains("QuanLyCheDo\\data\\bqp-data.db"),
                "DB test KHÔNG ĐƯỢC trỏ vào DB thực tế của người dùng: " + dbPath);
    }

    @Test
    void testUpsertPreservesValidationErrors() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String recId = "rec_upsert_" + uid;

        // 1. Tạo đơn vị và bản ghi ban đầu có validation error
        String unitTestId = "unit_upsert_" + uid;
        unitService.createUnit(UnitModel.builder()
                .id(unitTestId)
                .name("Đơn vị kiểm tra " + uid)
                .level(2)
                .build());

        PersonnelRecordModel r = PersonnelRecordModel.builder()
                .id(recId)
                .source("test_upsert")
                .unitId(unitTestId)
                .fullName("Nguyễn Văn Kiểm Tra " + uid)
                .sheetType("I.1")
                .categoryCode("SQ")
                .actualTotal(1000000.0)
                .calculatedTotal(1000000.0)
                .difference(0.0)
                .hasErrors(true)
                .errorDetails(List.of(
                        ValidationErrorModel.builder()
                                .columnNumber(9)
                                .errorCode("ERR_SALARY_MISMATCH")
                                .message("Lương sai lệch cần xem lại")
                                .build()
                ))
                .build();

        recordService.saveBatch(List.of(r));

        // 2. Cập nhật cùng ID qua UPSERT (chỉ đổi họ tên hoặc số tiền mà không gửi errorDetails mới)
        PersonnelRecordModel rUpdated = PersonnelRecordModel.builder()
                .id(recId)
                .source("test_upsert")
                .unitId(unitTestId)
                .fullName("Nguyễn Văn Kiểm Tra Đã Sửa " + uid)
                .sheetType("I.1")
                .categoryCode("SQ")
                .actualTotal(1200000.0)
                .calculatedTotal(1200000.0)
                .difference(0.0)
                .hasErrors(true)
                .build(); // Không truyền errorDetails mới

        recordService.saveBatch(List.of(rUpdated));

        // 3. Kiểm tra: bản ghi được cập nhật tên/tiền và lỗi validation_errors KHÔNG bị xóa mất
        PersonnelRecordModel loaded = recordService.findById(recId).orElse(null);
        assertNotNull(loaded);
        assertEquals("Nguyễn Văn Kiểm Tra Đã Sửa " + uid, loaded.getFullName());
        assertEquals(1200000.0, loaded.getActualTotal(), 0.01);
        assertNotNull(loaded.getErrorDetails());
        assertFalse(loaded.getErrorDetails().isEmpty(), "Lỗi validation_errors không bị CASCADE DELETE nhờ UPSERT");
        assertEquals("ERR_SALARY_MISMATCH", loaded.getErrorDetails().get(0).getErrorCode());
    }

    @Test
    void testBackupAndRestore() {
        // Tạo backup
        Map<String, Object> backupResult = backupService.createBackup();
        assertEquals("SUCCESS", backupResult.get("status"));
        String backupFile = (String) backupResult.get("backupFile");
        assertNotNull(backupFile);

        // Kiểm tra danh sách backup
        List<Map<String, Object>> backups = backupService.listBackups();
        assertFalse(backups.isEmpty());

        // Khôi phục thử từ backup vừa tạo
        Map<String, Object> restoreResult = backupService.restore(backupFile);
        assertEquals("SUCCESS", restoreResult.get("status"));
        assertEquals("ok", restoreResult.get("postIntegrityCheck"));

        // Kiểm tra chống path traversal
        Map<String, Object> invalid1 = backupService.restore("../../../etc/passwd");
        assertEquals("FAILED", invalid1.get("status"));
        assertTrue(invalid1.get("error").toString().contains("không hợp lệ"));

        Map<String, Object> invalid2 = backupService.restore("C:\\Windows\\System32\\cmd.exe");
        assertEquals("FAILED", invalid2.get("status"));
    }

    @Test
    void testReplaceSourceAndFindAll() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String unitId = "unit_replace_" + uid;
        String sourceName = "excel_source_" + uid;

        unitService.createUnit(UnitModel.builder()
                .id(unitId)
                .name("Đơn vị test replace " + uid)
                .level(3)
                .build());

        // Đợt 1: Thêm 3 bản ghi cho sourceName
        List<PersonnelRecordModel> batch1 = List.of(
                PersonnelRecordModel.builder()
                        .id("r1_" + uid).source(sourceName).unitId(unitId).fullName("Quân nhân 1")
                        .sheetType("I.1").categoryCode("SQ").actualTotal(100.0).calculatedTotal(100.0).difference(0.0)
                        .hasErrors(true).errorDetails(List.of(ValidationErrorModel.builder().columnNumber(10).message("Lỗi tháng").build()))
                        .build(),
                PersonnelRecordModel.builder()
                        .id("r2_" + uid).source(sourceName).unitId(unitId).fullName("Quân nhân 2")
                        .sheetType("I.1").categoryCode("SQ").actualTotal(200.0).calculatedTotal(200.0).difference(0.0)
                        .hasErrors(false)
                        .build()
        );
        recordService.saveBatch(batch1);

        List<PersonnelRecordModel> loaded1 = recordService.findAllBySource(sourceName);
        assertEquals(2, loaded1.size());
        assertTrue(loaded1.stream().anyMatch(r -> r.getErrorDetails() != null && !r.getErrorDetails().isEmpty()));

        // Đợt 2: Gọi replaceSource thay bằng 1 bản ghi mới
        List<PersonnelRecordModel> batch2 = List.of(
                PersonnelRecordModel.builder()
                        .id("r3_" + uid).source(sourceName).unitId(unitId).fullName("Quân nhân 3 mới")
                        .sheetType("I.1").categoryCode("SQ").actualTotal(300.0).calculatedTotal(300.0).difference(0.0)
                        .hasErrors(true).errorDetails(List.of(ValidationErrorModel.builder().columnNumber(12).message("Lỗi năm BHXH").build()))
                        .build()
        );
        recordService.replaceSource(sourceName, batch2);

        // Kiểm tra: sourceName giờ chỉ còn 1 bản ghi (r3), r1 và r2 đã bị xóa sạch hoàn toàn
        List<PersonnelRecordModel> loaded2 = recordService.findAllBySource(sourceName);
        assertEquals(1, loaded2.size());
        assertEquals("r3_" + uid, loaded2.get(0).getId());
        assertEquals("Quân nhân 3 mới", loaded2.get(0).getFullName());
        assertNotNull(loaded2.get(0).getErrorDetails());
        assertEquals(1, loaded2.get(0).getErrorDetails().size());
        assertEquals(12, loaded2.get(0).getErrorDetails().get(0).getColumnNumber());
    }

    @Test
    void testSyncUnitsAndCascadeDelete() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String pId = "sync_p_" + uid;
        String cId = "sync_c_" + uid;

        List<UnitModel> units = List.of(
                UnitModel.builder().id(pId).name("Cha " + uid).level(2).build(),
                UnitModel.builder().id(cId).name("Con " + uid).level(3).parentId(pId).build()
        );

        // Sync cây
        unitService.syncUnits(units);
        assertTrue(unitService.getUnitById(pId).isPresent());
        assertTrue(unitService.getUnitById(cId).isPresent());

        // Xóa kèm cây con
        unitService.deleteUnitCascade(pId);
        assertFalse(unitService.getUnitById(pId).isPresent());
        assertFalse(unitService.getUnitById(cId).isPresent());
    }

    @Test
    void testMigrationIdempotencyWhenClientRetriesWithDifferentMigrationId() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String unitId = "mig_unit_" + uid;
        String recId = "mig_rec_" + uid;

        Map<String, Object> payload1 = new LinkedHashMap<>();
        payload1.put("migrationId", "mig_first_attempt_" + uid);
        payload1.put("exportedAt", "2026-09-29T10:00:00Z");
        payload1.put("checksum", "client_chk_1");
        payload1.put("units", List.of(
                Map.of("id", unitId, "name", "Đơn vị di chuyển " + uid, "level", 2)
        ));
        payload1.put("recordsManual", List.of(
                Map.of(
                        "id", recId,
                        "unitId", unitId,
                        "hoTen", "Nguyễn Văn Di Chuyển " + uid,
                        "sheet", "I.1",
                        "tongTienThucTe", 500000.0,
                        "errorDetails", List.of("Lỗi phụ cấp thâm niên")
                )
        ));

        // Lần gửi 1: Thành công
        Map<String, Object> res1 = migrationService.migrate(payload1);
        assertEquals("SUCCESS", res1.get("status"));
        String hash1 = (String) res1.get("contentHash");
        assertNotNull(hash1);

        // Lần gửi 2: Mô phỏng trình duyệt bị rớt mạng, gửi lại đúng nội dung nhưng migrationId mới và timestamp mới
        Map<String, Object> payload2 = new LinkedHashMap<>();
        payload2.put("migrationId", "mig_second_retry_" + uid);
        payload2.put("exportedAt", "2026-09-29T10:05:00Z");
        payload2.put("checksum", "client_chk_2");
        payload2.put("units", List.of(
                Map.of("id", unitId, "name", "Đơn vị di chuyển " + uid, "level", 2)
        ));
        payload2.put("recordsManual", List.of(
                Map.of(
                        "id", recId,
                        "unitId", unitId,
                        "hoTen", "Nguyễn Văn Di Chuyển " + uid,
                        "sheet", "I.1",
                        "tongTienThucTe", 500000.0,
                        "errorDetails", List.of("Lỗi phụ cấp thâm niên")
                )
        ));

        Map<String, Object> res2 = migrationService.migrate(payload2);
        // Phải nhận diện được trùng nội dung qua contentHash và trả ALREADY_COMPLETED
        assertEquals("ALREADY_COMPLETED", res2.get("status"));
        assertEquals(hash1, res2.get("contentHash"));

        // Kiểm tra validation errors không bị nhân đôi
        PersonnelRecordModel loaded = recordService.findById(recId).orElse(null);
        assertNotNull(loaded);
        assertEquals(1, loaded.getErrorDetails().size(), "Chi tiết lỗi không bị nhân đôi khi client retry");
    }

    @Test
    void testRestoreFailsAndRollsBackOnForeignKeyViolation() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String unitId = "unit_good_" + uid;
        unitService.createUnit(UnitModel.builder().id(unitId).name("Đơn vị chuẩn " + uid).level(1).build());

        // 1. Tạo backup hợp lệ ban đầu
        Map<String, Object> goodBackup = backupService.createBackup();
        assertEquals("SUCCESS", goodBackup.get("status"));
        String goodBackupFile = (String) goodBackup.get("backupFile");
        assertNotNull(goodBackupFile);

        // 2. Tạo một file database backup giả lập bị vi phạm khóa ngoại
        java.nio.file.Path goodPath = java.nio.file.Paths.get(goodBackupFile);
        java.nio.file.Path backupsDir = goodPath.getParent();
        java.nio.file.Path badBackupPath = backupsDir.resolve("bqp-bad-fk-" + uid + ".db");

        try {
            java.nio.file.Files.copy(goodPath, badBackupPath);
            try (java.sql.Connection badConn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + badBackupPath.toAbsolutePath().toString().replace('\\', '/'));
                 java.sql.Statement stmt = badConn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = OFF;");
                stmt.execute("INSERT INTO personnel_records (id, source, unit_id, sheet_type, full_name, created_at, updated_at) VALUES ('bad_rec_" + uid + "', 'manual', 'non_existing_unit_id', 'I.1', 'Bản ghi ma', '2026-09-29', '2026-09-29');");
            }

            // 3. Khôi phục từ bad backup -> Hệ thống phải phát hiện foreign_key_check và FAILED
            Map<String, Object> restoreBad = backupService.restore(badBackupPath.getFileName().toString());
            assertEquals("FAILED", restoreBad.get("status"));
            assertTrue(restoreBad.get("error").toString().contains("foreign_key_check"),
                    "Thông báo lỗi phải đề cập foreign_key_check: " + restoreBad.get("error"));

            // 4. Kiểm tra database hiện tại đã rollback về checkpoint an toàn và không có bản ghi ma
            assertFalse(recordService.findById("bad_rec_" + uid).isPresent());
            Map<String, Object> integrity = integrityService.checkIntegrity();
            assertEquals("PASSED", integrity.get("foreignKeyCheck"));
        } catch (Exception e) {
            fail("Lỗi kiểm thử restore FK check: " + e.getMessage());
        } finally {
            try { java.nio.file.Files.deleteIfExists(badBackupPath); } catch (Exception ignored) {}
        }
    }

    @Test
    void testSyncUnitsReconciliation() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String u1 = "u1_" + uid;
        String u2 = "u2_" + uid;
        String u3 = "u3_" + uid;

        // Ban đầu có 3 đơn vị
        List<UnitModel> initial = List.of(
                UnitModel.builder().id(u1).name("Đơn vị 1 " + uid).level(1).build(),
                UnitModel.builder().id(u2).name("Đơn vị 2 " + uid).level(2).parentId(u1).build(),
                UnitModel.builder().id(u3).name("Đơn vị 3 " + uid).level(2).parentId(u1).build()
        );
        unitService.syncUnits(initial);
        assertTrue(unitService.getUnitById(u1).isPresent());
        assertTrue(unitService.getUnitById(u2).isPresent());
        assertTrue(unitService.getUnitById(u3).isPresent());

        // Lần sync 2: Loại bỏ u3 khỏi danh sách (u3 không có records) -> u3 phải bị xóa khỏi SQLite
        List<UnitModel> syncedWithoutU3 = List.of(
                UnitModel.builder().id(u1).name("Đơn vị 1 " + uid).level(1).build(),
                UnitModel.builder().id(u2).name("Đơn vị 2 " + uid).level(2).parentId(u1).build()
        );
        unitService.syncUnits(syncedWithoutU3);
        assertTrue(unitService.getUnitById(u1).isPresent());
        assertTrue(unitService.getUnitById(u2).isPresent());
        assertFalse(unitService.getUnitById(u3).isPresent(), "Đơn vị u3 không còn trong danh sách sync phải bị xóa khỏi SQLite");

        // Thêm hồ sơ vào u2
        recordService.saveRecord(PersonnelRecordModel.builder()
                .id("rec_u2_" + uid)
                .source("manual")
                .unitId(u2)
                .fullName("Quân nhân đơn vị 2")
                .sheetType("I.1")
                .build());

        // Lần sync 3: Gửi danh sách chỉ có u1 (u2 đang có hồ sơ) -> Phải ném IllegalStateException (409 Conflict)
        List<UnitModel> syncedWithoutU2 = List.of(
                UnitModel.builder().id(u1).name("Đơn vị 1 " + uid).level(1).build()
        );
        assertThrows(IllegalStateException.class, () -> unitService.syncUnits(syncedWithoutU2));
        assertTrue(unitService.getUnitById(u2).isPresent(), "Đơn vị có hồ sơ không được phép bị xóa khi sync");
    }

    @Test
    void testUnitSyncConflictWhenUnitHasRecords() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String u1 = "u_conf_1_" + uid;
        String u2 = "u_conf_2_" + uid;

        unitService.syncUnits(List.of(
                UnitModel.builder().id(u1).name("Đơn vị Gốc " + uid).level(1).build(),
                UnitModel.builder().id(u2).name("Đơn vị Con " + uid).level(2).parentId(u1).build()
        ));

        // Thêm hồ sơ vào u2
        recordService.saveRecord(PersonnelRecordModel.builder()
                .id("rec_conf_" + uid)
                .source("manual")
                .unitId(u2)
                .fullName("Quân nhân xung đột " + uid)
                .sheetType("I.1")
                .build());

        // Cố tình sync mà loại bỏ u2 (nhưng u2 đang chứa hồ sơ)
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            unitService.syncUnits(List.of(
                    UnitModel.builder().id(u1).name("Đơn vị Gốc " + uid).level(1).build()
            ));
        });

        assertTrue(ex.getMessage().contains("đang chứa dữ liệu"), "Thông báo lỗi phải cảnh báo đơn vị đang chứa dữ liệu: " + ex.getMessage());
        assertTrue(unitService.getUnitById(u2).isPresent(), "Đơn vị u2 phải còn nguyên vẹn sau khi rollback");
        assertTrue(recordService.findById("rec_conf_" + uid).isPresent(), "Hồ sơ trong u2 phải còn nguyên vẹn sau khi rollback");
    }

    @Test
    void testV2MigrationWithLegacyAndDuplicateChecksums() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String testDbPath = TEST_STORAGE_DIR + "/legacy_test_" + uid + ".db";
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get(TEST_STORAGE_DIR));

        // Giả lập database legacy ở schema V1 (chưa có partial unique index)
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + testDbPath);
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE migration_audit (" +
                    "migration_id TEXT PRIMARY KEY, " +
                    "started_at TEXT, " +
                    "completed_at TEXT, " +
                    "status TEXT, " +
                    "total_records INTEGER DEFAULT 0, " +
                    "checksum TEXT, " +
                    "error_message TEXT);");

            // Chèn dữ liệu legacy:
            // 1. Checksum không phải 64-hex (ví dụ format cũ 'mig_v1_3_10')
            stmt.execute("INSERT INTO migration_audit VALUES ('mig_legacy_1', '2026-09-01', '2026-09-01', 'SUCCESS', 10, 'mig_v1_3_10', NULL);");
            // 2. Checksum trùng lặp SHA-256
            String duplicateSha = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
            stmt.execute("INSERT INTO migration_audit VALUES ('mig_dup_1', '2026-09-02', '2026-09-02', 'SUCCESS', 5, '" + duplicateSha + "', NULL);");
            stmt.execute("INSERT INTO migration_audit VALUES ('mig_dup_2', '2026-09-03', '2026-09-03', 'SUCCESS', 5, '" + duplicateSha + "', NULL);");
            // 3. FAILED status
            stmt.execute("INSERT INTO migration_audit VALUES ('mig_failed_1', '2026-09-04', '2026-09-04', 'FAILED', 0, '" + duplicateSha + "', 'Error');");

            // Thực thi migration V2 cleanup queries
            stmt.execute("UPDATE migration_audit " +
                    "SET checksum = NULL " +
                    "WHERE checksum IS NOT NULL " +
                    "  AND (LENGTH(checksum) != 64 OR checksum GLOB '*[^0-9a-fA-F]*');");

            stmt.execute("UPDATE migration_audit " +
                    "SET checksum = NULL " +
                    "WHERE status = 'SUCCESS' " +
                    "  AND checksum IS NOT NULL " +
                    "  AND rowid NOT IN ( " +
                    "      SELECT MIN(rowid) " +
                    "      FROM migration_audit " +
                    "      WHERE status = 'SUCCESS' AND checksum IS NOT NULL " +
                    "      GROUP BY LOWER(checksum) " +
                    "  );");

            // Tạo Unique index thành công mà không bị lỗi UNIQUE constraint failed
            stmt.execute("CREATE UNIQUE INDEX idx_migration_audit_checksum_success " +
                    "ON migration_audit (LOWER(checksum)) " +
                    "WHERE status = 'SUCCESS' AND checksum IS NOT NULL;");

            // Kiểm tra kết quả
            try (java.sql.ResultSet rs = stmt.executeQuery("SELECT migration_id, checksum FROM migration_audit ORDER BY migration_id")) {
                while (rs.next()) {
                    String mId = rs.getString("migration_id");
                    String cs = rs.getString("checksum");
                    if ("mig_legacy_1".equals(mId)) {
                        assertNull(cs, "mig_legacy_1 checksum phải được dọn dẹp về NULL vì không phải 64-hex");
                    }
                }
            }
        } finally {
            try { java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(testDbPath)); } catch (Exception ignored) {}
        }
    }

    @Test
    void testPaginationAndLargeRecords() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String unitId = "u_page_" + uid;
        unitService.createUnit(UnitModel.builder().id(unitId).name("Đơn vị Phân trang " + uid).level(1).build());

        // Tạo 60 bản ghi mẫu
        List<PersonnelRecordModel> batch = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            String sheet = (i % 3 == 0) ? "I.1" : (i % 3 == 1 ? "I.2" : "I.3");
            double actual = 1000000.0 * (i + 1);
            double calculated = (i % 5 == 0) ? actual + 50000.0 : actual; // i % 5 == 0 có chênh lệch
            batch.add(PersonnelRecordModel.builder()
                    .id("rec_pg_" + uid + "_" + i)
                    .source("validated")
                    .unitId(unitId)
                    .sheetType(sheet)
                    .fullName("Quân nhân trang " + i + " " + uid)
                    .rank("Đại úy")
                    .actualTotal(actual)
                    .calculatedTotal(calculated)
                    .difference(calculated - actual)
                    .hasErrors(i % 5 == 0)
                    .build());
        }
        recordService.saveBatch(batch);

        // 1. Kiểm tra trang 0, kích thước 25
        Map<String, Object> p0 = recordService.findPaged("validated", unitId, "branch", null, null, null, null, 0, 25);
        List<?> items0 = (List<?>) p0.get("items");
        assertEquals(25, items0.size());
        assertEquals(60L, ((Number) p0.get("totalElements")).longValue());
        assertEquals(3, ((Number) p0.get("totalPages")).intValue());
        assertEquals(0, ((Number) p0.get("currentPage")).intValue());

        // 2. Kiểm tra lọc theo sheetType "I.1" (60 / 3 = 20 bản ghi)
        Map<String, Object> pSheet = recordService.findPaged("validated", unitId, "branch", "I.1", null, null, null, 0, 50);
        List<?> itemsSheet = (List<?>) pSheet.get("items");
        assertEquals(20, itemsSheet.size());
        assertEquals(20L, ((Number) pSheet.get("totalElements")).longValue());

        // 3. Kiểm tra lọc theo status "diff" (chênh lệch)
        Map<String, Object> pDiff = recordService.findPaged("validated", unitId, "branch", null, null, "diff", null, 0, 50);
        List<?> itemsDiff = (List<?>) pDiff.get("items");
        assertEquals(12, itemsDiff.size(), "Có 12 bản ghi chênh lệch (i % 5 == 0 trong 60 bản ghi)");

        // 4. Kiểm tra tìm kiếm từ khóa họ tên
        Map<String, Object> pSearch = recordService.findPaged("validated", unitId, "branch", null, null, null, "trang 15", 0, 10);
        List<?> itemsSearch = (List<?>) pSearch.get("items");
        assertEquals(1, itemsSearch.size());
    }

    @Test
    void testMaintenanceLockHoldsReadLockUntilTransactionAfterCompletion() throws Exception {
        // 1. Tạo backup an toàn trước
        Map<String, Object> backupRes = backupService.createBackup();
        assertEquals("SUCCESS", backupRes.get("status"));
        String backupFile = (String) backupRes.get("backupFile");

        java.util.concurrent.CountDownLatch txInsideLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch allowCommitLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch transactionCompletedLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean restoreAttempted = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean restoreCompletedBeforeTx = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicReference<Map<String, Object>> restoreResultRef = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> threadAEx = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> threadBEx = new java.util.concurrent.atomic.AtomicReference<>();

        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);

        // Thread A: Chạy trong transaction, ghi hồ sơ, giữ read lock qua TransactionSynchronization
        Thread threadA = new Thread(() -> {
            try {
                txTemplate.execute(status -> {
                    // Đăng ký đồng bộ của test TRƯỚC để afterCompletion của test chạy TRƯỚC callback của StorageMaintenanceLock
                    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                            new org.springframework.transaction.support.TransactionSynchronization() {
                                @Override
                                public void beforeCommit(boolean readOnly) {
                                    txInsideLatch.countDown();
                                    try {
                                        // Giữ Thread A ở đây trước khi JDBC commit hoàn tất
                                        allowCommitLatch.await(3, java.util.concurrent.TimeUnit.SECONDS);
                                    } catch (InterruptedException ignored) {}
                                }

                                @Override
                                public void afterCompletion(int status) {
                                    // Báo hiệu JDBC commit đã xong và callback afterCompletion đã thực thi
                                    transactionCompletedLatch.countDown();
                                }
                            }
                    );

                    recordService.saveRecord(PersonnelRecordModel.builder()
                            .id("rec_tx_lock_test")
                            .source("manual")
                            .fullName("Quân nhân Transaction Lock Test")
                            .sheetType("I.1")
                            .build());

                    return null;
                });
            } catch (Throwable t) {
                threadAEx.set(t);
            }
        });

        // Thread B: Thử restore trong lúc Thread A đang giữ transaction
        Thread threadB = new Thread(() -> {
            try {
                // Chờ Thread A vào sâu trong transaction và giữ read lock
                txInsideLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
                restoreAttempted.set(true);

                // Restore sẽ bị chặn bởi exclusive maintenance lock cho đến khi allowCommitLatch mở và Thread A commit + afterCompletion xong
                Map<String, Object> res = backupService.restore(backupFile);
                restoreResultRef.set(res);

                // Kiểm tra xem lúc restore hoàn thành thì afterCompletion của Thread A đã chạy xong chưa
                if (transactionCompletedLatch.getCount() > 0) {
                    restoreCompletedBeforeTx.set(true);
                }
            } catch (Throwable t) {
                threadBEx.set(t);
            }
        });

        threadA.start();
        threadB.start();

        // Chờ Thread B bắt đầu gọi restore (đang bị block bởi read lock của Thread A)
        Thread.sleep(300);
        assertTrue(restoreAttempted.get(), "Thread B phải đã bắt đầu gọi restore()");
        assertFalse(restoreCompletedBeforeTx.get(), "Restore KHÔNG ĐƯỢC PHÉP hoàn thành trước khi afterCompletion của Thread A kết thúc");

        // Cho phép Thread A commit hoàn tất -> nhả read lock trong afterCompletion
        allowCommitLatch.countDown();

        threadA.join(5000);
        threadB.join(5000);

        assertFalse(threadA.isAlive(), "Thread A phải kết thúc hoàn toàn");
        assertFalse(threadB.isAlive(), "Thread B phải kết thúc hoàn toàn");
        assertNull(threadAEx.get(), "Thread A không được có lỗi: " + (threadAEx.get() != null ? threadAEx.get().getMessage() : ""));
        assertNull(threadBEx.get(), "Thread B không được có lỗi: " + (threadBEx.get() != null ? threadBEx.get().getMessage() : ""));
        assertNotNull(restoreResultRef.get(), "Restore phải hoàn tất sau khi Thread A commit xong");
        assertEquals("SUCCESS", restoreResultRef.get().get("status"), "Restore thành công sau khi transaction nhả khóa");
        assertEquals(0, transactionCompletedLatch.getCount(), "afterCompletion của Thread A phải đã được gọi");
        assertFalse(restoreCompletedBeforeTx.get(), "Chứng minh: Restore chỉ hoàn tất sau khi afterCompletion nhả read lock");
    }

    @Test
    void testSequentialMigrationsV1V2V3OnRealDatabase() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String testDbPath = TEST_STORAGE_DIR + "/seq_mig_test_" + uid + ".db";
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get(TEST_STORAGE_DIR));

        // Khởi tạo một SQLite DB mới tinh và chạy tuần tự V1 -> V2 -> V3
        org.sqlite.SQLiteDataSource ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + testDbPath.replace('\\', '/'));
        org.springframework.jdbc.core.JdbcTemplate seqJdbc = new org.springframework.jdbc.core.JdbcTemplate(ds);

        com.bqpvalidateexcel.storage.migration.SqliteSchemaMigrator migrator =
                new com.bqpvalidateexcel.storage.migration.SqliteSchemaMigrator(seqJdbc);
        migrator.migrate();

        // Kiểm tra bảng schema_version có cả 5 phiên bản V1, V2, V3, V4, V5 và đều SUCCESS
        List<Map<String, Object>> versions = seqJdbc.queryForList("SELECT version, script_name, success FROM schema_version ORDER BY version ASC");
        assertEquals(6, versions.size(), "Phải áp dụng thành công cả 6 migration V1 đến V6");
        assertEquals(1, ((Number) versions.get(0).get("version")).intValue());
        assertEquals("V1__initial_schema.sql", versions.get(0).get("script_name"));
        assertEquals(1, ((Number) versions.get(0).get("success")).intValue());

        assertEquals(2, ((Number) versions.get(1).get("version")).intValue());
        assertEquals("V2__migration_idempotency_and_constraints.sql", versions.get(1).get("script_name"));
        assertEquals(1, ((Number) versions.get(1).get("success")).intValue());

        assertEquals(3, ((Number) versions.get(2).get("version")).intValue());
        assertEquals("V3__cleanup_legacy_migration_checksums.sql", versions.get(2).get("script_name"));
        assertEquals(1, ((Number) versions.get(2).get("success")).intValue());

        assertEquals(4, ((Number) versions.get(3).get("version")).intValue());
        assertEquals("V4__unit_4_level_hierarchy_and_audit.sql", versions.get(3).get("script_name"));
        assertEquals(1, ((Number) versions.get(3).get("success")).intValue());

        assertEquals(5, ((Number) versions.get(4).get("version")).intValue());
        assertEquals("V5__batch_import_metadata.sql", versions.get(4).get("script_name"));
        assertEquals(1, ((Number) versions.get(4).get("success")).intValue());

        assertEquals(6, ((Number) versions.get(5).get("version")).intValue());
        assertEquals("V6__unit_unique_canonical_index.sql", versions.get(5).get("script_name"));
        assertEquals(1, ((Number) versions.get(5).get("success")).intValue());

        // Kiểm tra chỉ mục unique trên migration_audit(checksum)
        seqJdbc.execute("INSERT INTO migration_audit (id, migration_id, source_type, checksum, status, created_at) " +
                "VALUES ('m1', 'mig_1', 'json', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', 'SUCCESS', '2026-09-29T10:00:00Z')");

        // Cố tình chèn trùng checksum SUCCESS -> Phải bị chặn bởi UNIQUE constraint
        assertThrows(Exception.class, () -> {
            seqJdbc.execute("INSERT INTO migration_audit (id, migration_id, source_type, checksum, status, created_at) " +
                    "VALUES ('m2', 'mig_2', 'json', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', 'SUCCESS', '2026-09-29T10:05:00Z')");
        });

        // Chèn cùng checksum nhưng FAILED -> Được phép (partial unique index)
        seqJdbc.execute("INSERT INTO migration_audit (id, migration_id, source_type, checksum, status, created_at) " +
                "VALUES ('m3', 'mig_3', 'json', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', 'FAILED', '2026-09-29T10:10:00Z')");

        try { java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(testDbPath)); } catch (Exception ignored) {}
    }

    @Test
    void testLargeDatasetPerformanceBenchmark() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String unitParent = "u_perf_p_" + uid;
        String unitChild = "u_perf_c_" + uid;

        unitService.createUnit(UnitModel.builder().id(unitParent).name("Bộ Chỉ Huy Hiệu Năng " + uid).level(2).build());
        unitService.createUnit(UnitModel.builder().id(unitChild).name("Ban Chỉ Huy Đơn Vị " + uid).level(3).parentId(unitParent).build());

        // Tạo 5.000 hồ sơ để kiểm thử hiệu năng truy vấn phân trang, đếm và tính toán
        int recordCount = 5000;
        List<PersonnelRecordModel> largeBatch = new ArrayList<>(recordCount);
        for (int i = 0; i < recordCount; i++) {
            String uId = (i % 2 == 0) ? unitParent : unitChild;
            String sheet = (i % 3 == 0) ? "I.1" : (i % 3 == 1 ? "I.2" : "I.3");
            double actual = 5000000.0 + (i * 1000.0);
            double calc = (i % 7 == 0) ? actual + 15000.0 : actual; // chênh lệch
            largeBatch.add(PersonnelRecordModel.builder()
                    .id("perf_rec_" + uid + "_" + i)
                    .source("validated")
                    .unitId(uId)
                    .sheetType(sheet)
                    .fullName("Quân nhân hiệu năng " + i + " " + uid)
                    .rank(i % 4 == 0 ? "Thượng tá" : "Thiếu tá")
                    .position("Trợ lý kế toán " + i)
                    .actualTotal(actual)
                    .calculatedTotal(calc)
                    .difference(calc - actual)
                    .hasErrors(i % 7 == 0)
                    .build());
        }

        long insertStart = System.currentTimeMillis();
        recordService.saveBatch(largeBatch);
        long insertTime = System.currentTimeMillis() - insertStart;
        System.out.println("[Benchmark] Chèn " + recordCount + " hồ sơ vào SQLite: " + insertTime + " ms");

        // 1. Đo hiệu năng countRecords (phải < 150ms)
        long countStart = System.currentTimeMillis();
        Map<String, Object> pagedFirst = recordService.findPaged("validated", unitParent, "branch", null, null, null, null, 0, 50);
        long countTime = System.currentTimeMillis() - countStart;
        System.out.println("[Benchmark] Đếm và phân trang trang đầu (" + recordCount + " bản ghi): " + countTime + " ms");
        assertEquals(50, ((List<?>) pagedFirst.get("items")).size());
        assertEquals((long) recordCount, ((Number) pagedFirst.get("totalElements")).longValue());
        assertTrue(countTime < 150, "Đếm và truy vấn phân trang phải < 150ms trên máy thử nghiệm, thực tế: " + countTime + " ms");

        // 2. Đo hiệu năng truy vấn phân trang có bộ lọc phức tạp (lọc theo đơn vị branch, sheet, chênh lệch, tìm kiếm tên)
        long queryStart = System.currentTimeMillis();
        Map<String, Object> paged = recordService.findPaged("validated", unitParent, "branch", "I.1", null, "diff", "hiệu năng 10", 0, 25);
        long queryTime = System.currentTimeMillis() - queryStart;
        System.out.println("[Benchmark] Truy vấn phân trang lọc phức tạp (branch + sheet + diff + search): " + queryTime + " ms");
        assertTrue(queryTime < 100, "Truy vấn phân trang có chỉ mục SQLite phải < 100ms, thực tế: " + queryTime + " ms");
        assertNotNull(paged.get("items"));

        // 3. Đo hiệu năng Rollup tổng hợp 5.000 hồ sơ
        long rollupStart = System.currentTimeMillis();
        Map<String, Object> rollup = rollupService.calculateRollup(unitParent, "branch");
        long rollupTime = System.currentTimeMillis() - rollupStart;
        System.out.println("[Benchmark] Tổng hợp Rollup toàn bộ 5.000 hồ sơ: " + rollupTime + " ms");
        assertTrue(rollupTime < 250, "Rollup tổng hợp toàn bộ nhánh 5.000 hồ sơ phải < 250ms, thực tế: " + rollupTime + " ms");
        Map<String, Object> overall = (Map<String, Object>) ((Map<String, Object>) rollup.get("branchTotals")).get("overall");
        assertEquals(recordCount, ((Number) overall.get("total_records")).intValue());
    }

    @Test
    void testMigrationFromDirtyV1DatabaseWithDuplicateChecksums() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String testDbPath = TEST_STORAGE_DIR + "/dirty_v1_mig_" + uid + ".db";
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get(TEST_STORAGE_DIR));

        org.sqlite.SQLiteDataSource ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + testDbPath.replace('\\', '/'));
        org.springframework.jdbc.core.JdbcTemplate dirtyJdbc = new org.springframework.jdbc.core.JdbcTemplate(ds);

        try {
            // 1. Tạo lược đồ V1 nguyên bản trên database trắng
            dirtyJdbc.execute("CREATE TABLE schema_version (" +
                    "version INTEGER PRIMARY KEY, " +
                    "script_name TEXT NOT NULL, " +
                    "checksum TEXT, " +
                    "installed_at TEXT NOT NULL, " +
                    "execution_time_ms INTEGER, " +
                    "success INTEGER NOT NULL);");

            dirtyJdbc.execute("CREATE TABLE units (" +
                    "id TEXT PRIMARY KEY, " +
                    "name TEXT NOT NULL, " +
                    "normalized_name TEXT, " +
                    "code TEXT, " +
                    "parent_id TEXT, " +
                    "level INTEGER NOT NULL DEFAULT 4, " +
                    "unit_type TEXT, " +
                    "display_order INTEGER DEFAULT 0, " +
                    "status TEXT NOT NULL DEFAULT 'ACTIVE', " +
                    "aliases_json TEXT, " +
                    "created_at TEXT NOT NULL, " +
                    "updated_at TEXT NOT NULL);");

            dirtyJdbc.execute("CREATE TABLE personnel_records (" +
                    "id TEXT PRIMARY KEY, " +
                    "source TEXT NOT NULL, " +
                    "unit_id TEXT NOT NULL, " +
                    "sheet_type TEXT NOT NULL, " +
                    "category_code TEXT, " +
                    "full_name TEXT NOT NULL, " +
                    "rank TEXT, " +
                    "position TEXT, " +
                    "birth_date TEXT, " +
                    "enlistment_date TEXT, " +
                    "merger_date TEXT, " +
                    "retirement_date TEXT, " +
                    "monthly_salary REAL DEFAULT 0, " +
                    "actual_total REAL DEFAULT 0, " +
                    "calculated_total REAL DEFAULT 0, " +
                    "difference REAL DEFAULT 0, " +
                    "has_errors INTEGER DEFAULT 0, " +
                    "input_json TEXT, " +
                    "result_json TEXT, " +
                    "raw_columns_json TEXT, " +
                    "created_at TEXT NOT NULL, " +
                    "updated_at TEXT NOT NULL);");

            dirtyJdbc.execute("CREATE TABLE validation_errors (" +
                    "id TEXT PRIMARY KEY, " +
                    "record_id TEXT NOT NULL, " +
                    "error_code TEXT NOT NULL, " +
                    "error_message TEXT NOT NULL, " +
                    "field_name TEXT, " +
                    "severity TEXT DEFAULT 'ERROR', " +
                    "created_at TEXT NOT NULL);");

            dirtyJdbc.execute("CREATE TABLE application_settings (" +
                    "key TEXT PRIMARY KEY, " +
                    "value TEXT NOT NULL, " +
                    "updated_at TEXT NOT NULL);");

            dirtyJdbc.execute("CREATE TABLE migration_audit (" +
                    "id TEXT PRIMARY KEY, " +
                    "migration_id TEXT NOT NULL, " +
                    "source_type TEXT NOT NULL, " +
                    "record_count INTEGER DEFAULT 0, " +
                    "unit_count INTEGER DEFAULT 0, " +
                    "checksum TEXT, " +
                    "status TEXT NOT NULL, " +
                    "details_json TEXT, " +
                    "created_at TEXT NOT NULL);");

            // Đánh dấu V1 đã được cài đặt thành công trước đó với checksum thật của V1
            String v1Checksum = getMigrationChecksum("V1__initial_schema.sql");
            dirtyJdbc.execute("INSERT INTO schema_version (version, script_name, checksum, installed_at, execution_time_ms, success) " +
                    "VALUES (1, 'V1__initial_schema.sql', '" + v1Checksum + "', '2026-09-01T00:00:00Z', 10, 1);");

            // 2. Chèn dữ liệu legacy bẩn vào migration_audit:
            String duplicateChecksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
            String uniqueValidChecksum = "1111111111111111111111111111111111111111111111111111111111111111";

            // - Hai dòng SUCCESS trùng checksum SHA-256
            dirtyJdbc.execute("INSERT INTO migration_audit VALUES ('m_dup_1', 'mig_dup_1', 'json', 10, 2, '" + duplicateChecksum + "', 'SUCCESS', NULL, '2026-09-02T00:00:00Z');");
            dirtyJdbc.execute("INSERT INTO migration_audit VALUES ('m_dup_2', 'mig_dup_2', 'json', 10, 2, '" + duplicateChecksum + "', 'SUCCESS', NULL, '2026-09-03T00:00:00Z');");

            // - Một dòng SUCCESS có checksum legacy không hợp lệ (ngắn, không phải 64-hex)
            dirtyJdbc.execute("INSERT INTO migration_audit VALUES ('m_invalid', 'mig_inv', 'excel', 5, 1, 'legacy_checksum_not_hex', 'SUCCESS', NULL, '2026-09-04T00:00:00Z');");

            // - Một dòng SUCCESS có checksum 64-hex hợp lệ duy nhất
            dirtyJdbc.execute("INSERT INTO migration_audit VALUES ('m_unique', 'mig_uniq', 'manual', 1, 1, '" + uniqueValidChecksum + "', 'SUCCESS', NULL, '2026-09-05T00:00:00Z');");

            // - Một dòng FAILED có trùng checksum (để kiểm tra partial index chỉ bắt SUCCESS)
            dirtyJdbc.execute("INSERT INTO migration_audit VALUES ('m_failed', 'mig_fail', 'json', 0, 0, '" + duplicateChecksum + "', 'FAILED', 'Error', '2026-09-06T00:00:00Z');");

            // 3. Thực thi SqliteSchemaMigrator thật trên database bẩn này
            com.bqpvalidateexcel.storage.migration.SqliteSchemaMigrator migrator =
                    new com.bqpvalidateexcel.storage.migration.SqliteSchemaMigrator(dirtyJdbc);

            // Bước này KHÔNG ĐƯỢC PHÉP ném SQLITE_CONSTRAINT_UNIQUE vì preflight đã dọn dẹp an toàn trước V2
            assertDoesNotThrow(migrator::migrate, "SqliteSchemaMigrator phải nâng cấp mượt mà từ V1 bẩn lên V2 và V3");

            // 4. Kiểm chứng kết quả nâng cấp
            List<Map<String, Object>> versions = dirtyJdbc.queryForList("SELECT version, script_name, success FROM schema_version ORDER BY version ASC");
            assertEquals(6, versions.size(), "Cả 6 bản V1 đến V6 phải có mặt trong schema_version");
            assertEquals(1, ((Number) versions.get(1).get("success")).intValue(), "V2 phải thành công");
            assertEquals(2, ((Number) versions.get(1).get("version")).intValue());
            assertEquals(1, ((Number) versions.get(2).get("success")).intValue(), "V3 phải thành công");
            assertEquals(3, ((Number) versions.get(2).get("version")).intValue());
            assertEquals(1, ((Number) versions.get(3).get("success")).intValue(), "V4 phải thành công");
            assertEquals(4, ((Number) versions.get(3).get("version")).intValue());
            assertEquals(1, ((Number) versions.get(4).get("success")).intValue(), "V5 phải thành công");
            assertEquals(5, ((Number) versions.get(4).get("version")).intValue());
            assertEquals(1, ((Number) versions.get(5).get("success")).intValue(), "V6 phải thành công");
            assertEquals(6, ((Number) versions.get(5).get("version")).intValue());

            // 5. Kiểm tra dữ liệu trong migration_audit
            Map<String, Object> invalidRow = dirtyJdbc.queryForMap("SELECT checksum FROM migration_audit WHERE id = 'm_invalid'");
            assertNull(invalidRow.get("checksum"), "Checksum legacy không phải 64-hex phải bị dọn dẹp về NULL");

            Map<String, Object> dupRow1 = dirtyJdbc.queryForMap("SELECT checksum FROM migration_audit WHERE id = 'm_dup_1'");
            Map<String, Object> dupRow2 = dirtyJdbc.queryForMap("SELECT checksum FROM migration_audit WHERE id = 'm_dup_2'");

            // Một trong 2 dòng giữ lại duplicateChecksum, dòng còn lại phải là NULL
            boolean oneHasChecksumAndOtherNull =
                    (duplicateChecksum.equals(dupRow1.get("checksum")) && dupRow2.get("checksum") == null) ||
                    (duplicateChecksum.equals(dupRow2.get("checksum")) && dupRow1.get("checksum") == null);
            assertTrue(oneHasChecksumAndOtherNull, "Chỉ duy nhất 1 bản ghi SUCCESS được giữ checksum, bản ghi trùng lặp phải là NULL");

            Map<String, Object> uniqueRow = dirtyJdbc.queryForMap("SELECT checksum FROM migration_audit WHERE id = 'm_unique'");
            assertEquals(uniqueValidChecksum, uniqueRow.get("checksum"), "Checksum hợp lệ duy nhất phải được bảo toàn");

            // 6. Kiểm tra partial unique index idx_migration_audit_checksum_success thực sự hoạt động
            assertThrows(Exception.class, () -> {
                dirtyJdbc.execute("INSERT INTO migration_audit VALUES ('m_dup_violate', 'mig_v', 'json', 1, 1, '" + uniqueValidChecksum + "', 'SUCCESS', NULL, '2026-09-07T00:00:00Z');");
            }, "Chèn trùng checksum SUCCESS sau migration phải bị chặn bởi UNIQUE constraint");

        } finally {
            try { java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(testDbPath)); } catch (Exception ignored) {}
        }
    }

    @Test
    void testSqliteSchemaMigratorTransitionChecksumAndIntegrity() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String testDbPath = TEST_STORAGE_DIR + "/checksum_test_" + uid + ".db";
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get(TEST_STORAGE_DIR));

        org.sqlite.SQLiteDataSource ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + testDbPath.replace('\\', '/'));
        org.springframework.jdbc.core.JdbcTemplate testJdbc = new org.springframework.jdbc.core.JdbcTemplate(ds);

        try {
            testJdbc.execute("CREATE TABLE schema_version (" +
                    "version INTEGER PRIMARY KEY, " +
                    "script_name TEXT NOT NULL, " +
                    "checksum TEXT, " +
                    "installed_at TEXT NOT NULL, " +
                    "execution_time_ms INTEGER, " +
                    "success INTEGER NOT NULL);");

            testJdbc.execute("CREATE TABLE migration_audit (" +
                    "id TEXT PRIMARY KEY, " +
                    "migration_id TEXT NOT NULL, " +
                    "source_type TEXT NOT NULL, " +
                    "record_count INTEGER DEFAULT 0, " +
                    "unit_count INTEGER DEFAULT 0, " +
                    "checksum TEXT, " +
                    "status TEXT NOT NULL, " +
                    "details_json TEXT, " +
                    "created_at TEXT NOT NULL);");

            testJdbc.execute("CREATE TABLE units (" +
                    "id TEXT PRIMARY KEY, " +
                    "code TEXT, " +
                    "name TEXT NOT NULL, " +
                    "normalized_name TEXT, " +
                    "level INTEGER DEFAULT 1, " +
                    "unit_type TEXT, " +
                    "parent_id TEXT, " +
                    "display_order INTEGER DEFAULT 0, " +
                    "aliases_json TEXT, " +
                    "is_preset INTEGER DEFAULT 0, " +
                    "is_active INTEGER DEFAULT 1, " +
                    "created_at TEXT NOT NULL, " +
                    "updated_at TEXT NOT NULL);");

            // 1. Database từng ghi nhận checksum chuyển tiếp của V2 (từ đợt rà soát 5)
            String v1Checksum = getMigrationChecksum("V1__initial_schema.sql");
            testJdbc.execute("INSERT INTO schema_version VALUES (1, 'V1__initial_schema.sql', '" + v1Checksum + "', '2026-09-01T00:00:00Z', 10, 1);");
            testJdbc.execute("INSERT INTO schema_version VALUES (2, 'V2__migration_idempotency_and_constraints.sql', '4c1916f4c50a6340f925f23c347b7e27783ba0d92ff10e3948df4f6a2cef198b', '2026-09-02T00:00:00Z', 15, 1);");

            com.bqpvalidateexcel.storage.migration.SqliteSchemaMigrator migrator =
                    new com.bqpvalidateexcel.storage.migration.SqliteSchemaMigrator(testJdbc);

            // Migrator phải chấp nhận checksum chuyển tiếp đã biết mà không văng lỗi IllegalStateException
            assertDoesNotThrow(migrator::migrate, "Checksum chuyển tiếp của V2 phải được chấp nhận tương thích ngược an toàn");

            // 2. Database bị sửa script trái phép (checksum không nhận diện được)
            testJdbc.execute("UPDATE schema_version SET checksum = 'corrupted_unknown_checksum_12345' WHERE version = 2");
            RuntimeException integrityEx = assertThrows(RuntimeException.class, migrator::migrate);
            assertNotNull(integrityEx.getCause(), "Ngoại lệ phải có nguyên nhân gốc rễ");
            assertTrue(integrityEx.getCause() instanceof IllegalStateException, "Nguyên nhân phải là IllegalStateException");
            assertTrue(integrityEx.getCause().getMessage().contains("CẢNH BÁO TOÀN VẸN"), "Phải cảnh báo toàn vẹn khi script migration bị thay đổi checksum không hợp lệ");

        } finally {
            try { java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(testDbPath)); } catch (Exception ignored) {}
        }
    }

    @Test
    void testReadOperationsAndBackupBlockedDuringExclusiveRestore() {
        // Kiểm tra StorageMaintenanceLock cô lập hoàn toàn các thao tác đọc và backup khi ở chế độ bảo trì
        java.util.concurrent.atomic.AtomicBoolean readBlocked = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean backupBlocked = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean integrityBlocked = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean statusBlocked = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean unitDescendantsBlocked = new java.util.concurrent.atomic.AtomicBoolean(false);

        maintenanceLock.callWithExclusiveMaintenance(() -> {
            assertTrue(maintenanceLock.isMaintenanceMode(), "maintenanceMode phải là true trong khối bảo trì độc quyền");

            // 1. Thao tác đọc findPaged phải bị từ chối
            try {
                recordService.findPaged("validated", null, "branch", null, null, null, null, 0, 10);
            } catch (IllegalStateException e) {
                if (e.getMessage().contains("khôi phục dữ liệu") || e.getMessage().contains("bảo trì")) {
                    readBlocked.set(true);
                }
            }

            // 2. Thao tác tạo backup cũng phải bị từ chối trong khi restore
            try {
                backupService.createBackup();
            } catch (IllegalStateException e) {
                if (e.getMessage().contains("khôi phục dữ liệu") || e.getMessage().contains("bảo trì")) {
                    backupBlocked.set(true);
                }
            }

            // 3. Thao tác checkIntegrity phải bị từ chối
            try {
                integrityService.checkIntegrity();
            } catch (IllegalStateException e) {
                if (e.getMessage().contains("khôi phục dữ liệu") || e.getMessage().contains("bảo trì")) {
                    integrityBlocked.set(true);
                }
            }

            // 4. Thao tác getStorageStatus phải bị từ chối
            try {
                integrityService.getStorageStatus();
            } catch (IllegalStateException e) {
                if (e.getMessage().contains("khôi phục dữ liệu") || e.getMessage().contains("bảo trì")) {
                    statusBlocked.set(true);
                }
            }

            // 5. Thao tác getAllDescendantIds phải bị từ chối
            try {
                unitService.getAllDescendantIds("root_test_id");
            } catch (IllegalStateException e) {
                if (e.getMessage().contains("khôi phục dữ liệu") || e.getMessage().contains("bảo trì")) {
                    unitDescendantsBlocked.set(true);
                }
            }

            return null;
        });

        assertTrue(readBlocked.get(), "Thao tác đọc phải bị chặn hoàn toàn khi hệ thống đang trong exclusive maintenance lock");
        assertTrue(backupBlocked.get(), "Thao tác createBackup phải bị chặn hoàn toàn khi hệ thống đang trong exclusive maintenance lock");
        assertTrue(integrityBlocked.get(), "Thao tác checkIntegrity phải bị chặn hoàn toàn khi hệ thống đang trong exclusive maintenance lock");
        assertTrue(statusBlocked.get(), "Thao tác getStorageStatus phải bị chặn hoàn toàn khi hệ thống đang trong exclusive maintenance lock");
        assertTrue(unitDescendantsBlocked.get(), "Thao tác getAllDescendantIds phải bị chặn hoàn toàn khi hệ thống đang trong exclusive maintenance lock");
        assertFalse(maintenanceLock.isMaintenanceMode(), "maintenanceMode phải trở về false sau khi kết thúc khối bảo trì");
    }

    @Test
    void testExportSnapshotSessionConsistencyWithConcurrentModifications() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String unitId = "u_exp_sess_" + uid;
        unitService.createUnit(UnitModel.builder().id(unitId).name("Đơn vị Snapshot Export " + uid).level(1).build());

        // 1. Tạo 100 hồ sơ ban đầu
        List<PersonnelRecordModel> initialRecords = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            initialRecords.add(PersonnelRecordModel.builder()
                    .id("rec_exp_init_" + uid + "_" + String.format("%03d", i))
                    .source("validated")
                    .unitId(unitId)
                    .sheetType(i % 2 == 0 ? "I.1" : "I.2")
                    .fullName("Quân nhân xuất ban đầu " + i)
                    .actualTotal(1000000.0 * (i + 1))
                    .calculatedTotal(1000000.0 * (i + 1))
                    .difference(0.0)
                    .hasErrors(false)
                    .build());
        }
        recordService.saveBatch(initialRecords);

        // 2. Khởi tạo Export Snapshot Session
        Map<String, Object> session = recordService.createExportSession("validated", unitId, "branch", null, null, null, null);
        assertNotNull(session);
        String sessionId = (String) session.get("sessionId");
        assertNotNull(sessionId);
        assertEquals(100, ((Number) session.get("totalElements")).intValue());

        // 3. Trong khi phiên xuất đang mở, thực hiện chèn đồng thời 50 hồ sơ mới
        List<PersonnelRecordModel> concurrentInserts = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            concurrentInserts.add(PersonnelRecordModel.builder()
                    .id("rec_exp_concurrent_" + uid + "_" + String.format("%03d", i))
                    .source("validated")
                    .unitId(unitId)
                    .sheetType("I.1")
                    .fullName("Quân nhân chèn đồng thời " + i)
                    .actualTotal(2000000.0)
                    .calculatedTotal(2000000.0)
                    .difference(0.0)
                    .hasErrors(false)
                    .build());
        }
        recordService.saveBatch(concurrentInserts);

        // Cập nhật họ tên của 10 hồ sơ đầu tiên
        PersonnelRecordModel updated0 = recordService.findById("rec_exp_init_" + uid + "_000").orElseThrow();
        updated0.setFullName("Quân nhân đã sửa họ tên 0");
        recordService.saveRecord(updated0);

        // 4. Đọc các trang từ Snapshot Session (giả lập kích thước trang = 25 hồ sơ, tổng cộng 4 trang)
        List<PersonnelRecordModel> fetchedFromSession = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        int pageSize = 25;
        int totalPages = 4; // 100 / 25

        for (int page = 0; page < totalPages; page++) {
            Map<String, Object> pageData = recordService.getExportSessionPage(sessionId, page, pageSize, true);
            List<PersonnelRecordModel> items = (List<PersonnelRecordModel>) pageData.get("items");
            assertNotNull(items);
            assertEquals(25, items.size(), "Mỗi trang snapshot phải có đúng 25 hồ sơ");

            for (PersonnelRecordModel rec : items) {
                assertFalse(seenIds.contains(rec.getId()), "Hồ sơ không được trùng lặp giữa các trang: " + rec.getId());
                seenIds.add(rec.getId());
                // Khẳng định: KHÔNG CÓ bất kỳ hồ sơ nào trong 50 hồ sơ chèn đồng thời bị lọt vào phiên xuất
                assertFalse(rec.getId().startsWith("rec_exp_concurrent_"), "Hồ sơ mới chèn đồng thời KHÔNG được lọt vào snapshot: " + rec.getId());
            }
            fetchedFromSession.addAll(items);
        }

        // 5. Kiểm tra tính toàn vẹn 100% của phiên snapshot
        assertEquals(100, fetchedFromSession.size(), "Tổng số bản ghi tải từ snapshot session phải đúng bằng 100");
        // Khẳng định P1-03: Phiên Snapshot bất biến - dữ liệu sửa đổi sau khi tạo phiên KHÔNG làm đổi kết quả xuất
        assertEquals("Quân nhân xuất ban đầu 0", fetchedFromSession.get(0).getFullName(),
                "Phiên snapshot phải bảo lưu giá trị tại thời điểm tạo phiên, không bị ảnh hưởng bởi cập nhật đồng thời");

        // 6. Đóng phiên snapshot session và xác nhận đã giải phóng
        recordService.closeExportSession(sessionId);
        assertThrows(IllegalArgumentException.class, () -> {
            recordService.getExportSessionPage(sessionId, 0, pageSize, true);
        }, "Gọi lấy trang sau khi đóng phiên phải ném ngoại lệ");
    }

    @Test
    void testExportSnapshotSessionHttpJsonContract() throws Exception {
        // 1. Chuẩn bị dữ liệu mẫu
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String unitId = "unit_json_" + uid;
        unitService.createUnit(UnitModel.builder().id(unitId).name("Đơn vị JSON Test").code("UJSON_" + uid).level(1).build());

        List<PersonnelRecordModel> records = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            records.add(PersonnelRecordModel.builder()
                    .id("rec_json_" + uid + "_" + i)
                    .source("validated")
                    .unitId(unitId)
                    .sheetType("I.1")
                    .fullName("Quân nhân JSON " + i)
                    .monthlySalary(15000000.0)
                    .actualTotal(50000000.0)
                    .calculatedTotal(50000000.0)
                    .difference(0.0)
                    .hasErrors(false)
                    .build());
        }
        recordService.saveBatch(records);

        // 2. Kiểm thử hợp đồng HTTP JSON POST: Tạo Snapshot Session với RequestBody JSON
        String jsonPayload = "{\"source\":\"validated\",\"unitId\":\"" + unitId + "\",\"scope\":\"branch\",\"sheetType\":\"I.1\"}";
        String responseContent = mockMvc.perform(post("/api/records/export/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode rootNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseContent);
        String sessionId = rootNode.get("sessionId").asText();
        assertNotNull(sessionId);

        // 3. Đọc dữ liệu theo trang qua endpoint GET
        mockMvc.perform(get("/api/records/export/session/" + sessionId + "/page")
                        .param("page", "0")
                        .param("size", "10")
                        .param("includeErrors", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0].fullName").value("Quân nhân JSON 0"));

        // 4. Giải phóng phiên xuất qua DELETE
        mockMvc.perform(delete("/api/records/export/session/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.sessionId").value(sessionId));

        // 5. Kiểm tra truy cập lại sau khi đã đóng phiên -> Báo lỗi HTTP 400 Bad Request
        mockMvc.perform(get("/api/records/export/session/" + sessionId + "/page"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));

        // 6. Kiểm thử xử lý lỗi: JSON Payload thiếu source -> Báo lỗi HTTP 400 Bad Request
        mockMvc.perform(post("/api/records/export/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"\",\"unitId\":\"" + unitId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("source")));
    }

    @Test
    void testFourLevelHierarchyEnforcement() {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        // 1. Cấp 1: BQP / Cục Tài chính (parent_id = null)
        UnitModel level1 = unitService.createUnit(UnitModel.builder()
                .id("u1_" + uid)
                .name("Bộ Quốc Phòng " + uid)
                .code("BQP_" + uid)
                .build());
        assertNotNull(level1);
        assertEquals(1, level1.getLevel());
        assertNull(level1.getParentId());

        // 2. Cấp 2: Quân khu / Quân chủng (thuộc Cấp 1)
        UnitModel level2 = unitService.createUnit(UnitModel.builder()
                .id("u2_" + uid)
                .parentId(level1.getId())
                .name("Quân khu 1 " + uid)
                .code("QK1_" + uid)
                .build());
        assertEquals(2, level2.getLevel());
        assertEquals(level1.getId(), level2.getParentId());

        // 3. Cấp 3: Sư đoàn / Bộ CHQS tỉnh (thuộc Cấp 2)
        UnitModel level3 = unitService.createUnit(UnitModel.builder()
                .id("u3_" + uid)
                .parentId(level2.getId())
                .name("Sư đoàn 3 " + uid)
                .code("F3_" + uid)
                .build());
        assertEquals(3, level3.getLevel());

        // 4. Cấp 4: Trung đoàn / Ban CHQS huyện (thuộc Cấp 3)
        UnitModel level4 = unitService.createUnit(UnitModel.builder()
                .id("u4_" + uid)
                .parentId(level3.getId())
                .name("Trung đoàn 12 " + uid)
                .code("E12_" + uid)
                .build());
        assertEquals(4, level4.getLevel());

        // 5. Thử tạo Cấp 5 dưới Cấp 4 -> Phải ném ngoại lệ IllegalArgumentException
        IllegalArgumentException exLevel5 = assertThrows(IllegalArgumentException.class, () -> {
            unitService.createUnit(UnitModel.builder()
                    .id("u5_" + uid)
                    .parentId(level4.getId())
                    .name("Tiểu đoàn 1 " + uid)
                    .code("D1_" + uid)
                    .build());
        });
        assertTrue(exLevel5.getMessage().contains("vượt quá giới hạn 4 cấp"));

        // 6. Thử tạo trùng mã đơn vị -> Phải ném ngoại lệ
        IllegalArgumentException exDupCode = assertThrows(IllegalArgumentException.class, () -> {
            unitService.createUnit(UnitModel.builder()
                    .parentId(level1.getId())
                    .name("Đơn vị trùng mã " + uid)
                    .code("QK1_" + uid) // trùng mã level2
                    .build());
        });
        assertTrue(exDupCode.getMessage().contains("Mã đơn vị"));

        // 7. Thử tạo trùng tên đơn vị trong cùng đơn vị cha -> Phải ném ngoại lệ
        IllegalArgumentException exDupName = assertThrows(IllegalArgumentException.class, () -> {
            unitService.createUnit(UnitModel.builder()
                    .parentId(level1.getId())
                    .name("quân khu 1 " + uid) // trùng tên normalized
                    .code("QK1_DIFF_" + uid)
                    .build());
        });
        assertTrue(exDupName.getMessage().contains("tồn tại"));
    }

    @Test
    void testUnitSubtreeMoveAndCyclePrevention() {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        // Tạo cây 1: L1_A -> L2_A -> L3_A -> L4_A
        UnitModel l1A = unitService.createUnit(UnitModel.builder().id("l1a_" + uid).name("Root A " + uid).code("R_A_" + uid).build());
        UnitModel l2A = unitService.createUnit(UnitModel.builder().id("l2a_" + uid).parentId(l1A.getId()).name("L2 A " + uid).code("L2A_" + uid).build());
        UnitModel l3A = unitService.createUnit(UnitModel.builder().id("l3a_" + uid).parentId(l2A.getId()).name("L3 A " + uid).code("L3A_" + uid).build());
        UnitModel l4A = unitService.createUnit(UnitModel.builder().id("l4a_" + uid).parentId(l3A.getId()).name("L4 A " + uid).code("L4A_" + uid).build());

        // Tạo cây 2: L1_B -> L2_B -> L3_B
        UnitModel l1B = unitService.createUnit(UnitModel.builder().id("l1b_" + uid).name("Root B " + uid).code("R_B_" + uid).build());
        UnitModel l2B = unitService.createUnit(UnitModel.builder().id("l2b_" + uid).parentId(l1B.getId()).name("L2 B " + uid).code("L2B_" + uid).build());
        UnitModel l3B = unitService.createUnit(UnitModel.builder().id("l3b_" + uid).parentId(l2B.getId()).name("L3 B " + uid).code("L3B_" + uid).build());

        // 1. Chống chu trình: không thể chọn chính mình làm cha
        IllegalArgumentException exSelf = assertThrows(IllegalArgumentException.class, () -> {
            unitService.moveUnit(l2A.getId(), l2A.getId());
        });
        assertTrue(exSelf.getMessage().contains("chính mình"));

        // 2. Chống chu trình: không thể chuyển đơn vị cha vào đơn vị con (l2A -> l4A)
        IllegalStateException exCycle = assertThrows(IllegalStateException.class, () -> {
            unitService.moveUnit(l2A.getId(), l4A.getId());
        });
        assertTrue(exCycle.getMessage().contains("chu trình"));

        // 3. Giới hạn độ sâu: Cây của l2A có depth = 3 (l2A -> l3A -> l4A).
        // Nếu chuyển l2A sang dưới l3B (cấp 3), cấp mới sẽ là 3 + 3 = 6 > 4 -> Phải chặn
        IllegalArgumentException exDepth = assertThrows(IllegalArgumentException.class, () -> {
            unitService.moveUnit(l2A.getId(), l3B.getId());
        });
        assertTrue(exDepth.getMessage().contains("vượt quá cấp 4"));

        // 4. Chuyển hợp lệ: Chuyển l3A (gồm l3A và l4A, depth = 2) từ l2A sang l2B (cấp 2)
        // Cấp mới của l3A là 3, l4A là 4 -> Hợp lệ <= 4
        unitService.moveUnit(l3A.getId(), l2B.getId());
        UnitModel l3After = unitService.getUnitById(l3A.getId()).orElseThrow();
        UnitModel l4After = unitService.getUnitById(l4A.getId()).orElseThrow();
        assertEquals(l2B.getId(), l3After.getParentId());
        assertEquals(3, l3After.getLevel());
        assertEquals(4, l4After.getLevel());

        // 5. Kiểm tra lịch sử kiểm toán MOVE
        List<Map<String, Object>> history = unitService.getAuditHistory(l3A.getId());
        assertFalse(history.isEmpty());
        boolean hasMoveAudit = history.stream().anyMatch(h -> "MOVE".equals(h.get("action")));
        assertTrue(hasMoveAudit);
    }

    @Test
    void testUnitDeletionProtectionAndDeactivationCascade() {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        UnitModel l1 = unitService.createUnit(UnitModel.builder().id("d1_" + uid).name("Root D " + uid).code("RD_" + uid).build());
        UnitModel l2 = unitService.createUnit(UnitModel.builder().id("d2_" + uid).parentId(l1.getId()).name("L2 D " + uid).code("L2D_" + uid).build());

        // 1. Không thể xóa l1 vì có con là l2
        assertThrows(IllegalStateException.class, () -> unitService.deleteUnit(l1.getId()));

        // 2. Thêm hồ sơ vào l2
        PersonnelRecordModel rec = PersonnelRecordModel.builder()
                .id(UUID.randomUUID().toString())
                .unitId(l2.getId())
                .fullName("Quân nhân thử nghiệm")
                .actualTotal(100.0)
                .calculatedTotal(100.0)
                .difference(0.0)
                .sheetType("7b")
                .source("validated")
                .build();
        recordService.saveRecord(rec);

        // 3. Không thể xóa l2 vì đã có hồ sơ
        assertThrows(IllegalStateException.class, () -> unitService.deleteUnit(l2.getId()));

        // 4. Deactivate l1 -> Cascade deactive l2
        unitService.deactivateUnit(l1.getId());
        UnitModel l1Reload = unitService.getUnitById(l1.getId()).orElseThrow();
        UnitModel l2Reload = unitService.getUnitById(l2.getId()).orElseThrow();
        assertFalse(l1Reload.getIsActive());
        assertFalse(l2Reload.getIsActive());
    }

    @Test
    void testUnitInventoryReport() {
        Map<String, Object> inventory = unitService.getInventoryReport();
        assertNotNull(inventory);
        assertTrue(inventory.containsKey("totalUnits"));
        assertTrue(inventory.containsKey("level1Count"));
        assertTrue(inventory.containsKey("level2Count"));
        assertTrue(inventory.containsKey("level3Count"));
        assertTrue(inventory.containsKey("level4Count"));
        assertEquals(0, ((Number) inventory.get("invalidLevelCount")).intValue());
        assertEquals(0, ((List<?>) inventory.get("orphanUnits")).size());
        assertEquals(0, ((List<?>) inventory.get("duplicateCodes")).size());
        assertEquals(0, ((List<?>) inventory.get("duplicateNamesInParent")).size());
    }

    @Test
    void testRollupBySource() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        UnitModel u = unitService.createUnit(UnitModel.builder().id("ru_" + uid).name("Đơn vị Rollup " + uid).code("RU_" + uid).build());

        // Tạo 1 bản ghi validated
        recordService.saveRecord(PersonnelRecordModel.builder()
                .id(UUID.randomUUID().toString())
                .unitId(u.getId())
                .fullName("Quân nhân Validated")
                .actualTotal(500.0)
                .calculatedTotal(500.0)
                .difference(0.0)
                .sheetType("7b")
                .source("validated")
                .build());

        // Tạo 1 bản ghi manual
        recordService.saveRecord(PersonnelRecordModel.builder()
                .id(UUID.randomUUID().toString())
                .unitId(u.getId())
                .fullName("Quân nhân Manual")
                .actualTotal(300.0)
                .calculatedTotal(300.0)
                .difference(0.0)
                .sheetType("7b")
                .source("manual")
                .build());

        // 1. Kiểm tra lọc nguồn 'validated' qua RollupService
        Map<String, Object> resValidated = rollupService.calculateRollup(u.getId(), "direct", "validated");
        Map<String, Object> overallValidated = (Map<String, Object>) ((Map<String, Object>) resValidated.get("directTotals")).get("overall");
        assertEquals(1, ((Number) overallValidated.get("total_records")).intValue());
        assertEquals(500.0, ((Number) overallValidated.get("total_actual")).doubleValue(), 0.001);

        // 2. Nguồn 'manual': Chỉ tính 1 bản ghi manual
        Map<String, Object> resManual = rollupService.calculateRollup(u.getId(), "direct", "manual");
        Map<String, Object> overallManual = (Map<String, Object>) ((Map<String, Object>) resManual.get("directTotals")).get("overall");
        assertEquals(1, ((Number) overallManual.get("total_records")).intValue());
        assertEquals(300.0, ((Number) overallManual.get("total_actual")).doubleValue(), 0.001);

        // 3. Nguồn 'all': Tính cả 2 bản ghi
        Map<String, Object> resAll = rollupService.calculateRollup(u.getId(), "direct", "all");
        Map<String, Object> overallAll = (Map<String, Object>) ((Map<String, Object>) resAll.get("directTotals")).get("overall");
        assertEquals(2, ((Number) overallAll.get("total_records")).intValue());
        assertEquals(800.0, ((Number) overallAll.get("total_actual")).doubleValue(), 0.001);

        // 4. Kiểm tra HTTP Rollup API mặc định là 'validated'
        mockMvc.perform(get("/api/rollups/units/" + u.getId()).param("scope", "direct"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("validated"))
                .andExpect(jsonPath("$.directTotals.overall.total_records").value(1))
                .andExpect(jsonPath("$.directTotals.overall.total_actual").value(500.0));

        // 5. Kiểm tra HTTP Rollup API với param source=manual
        mockMvc.perform(get("/api/rollups/units/" + u.getId()).param("scope", "direct").param("source", "manual"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("manual"))
                .andExpect(jsonPath("$.directTotals.overall.total_records").value(1))
                .andExpect(jsonPath("$.directTotals.overall.total_actual").value(300.0));

        // 6. Kiểm tra HTTP Rollup API với param source=all
        mockMvc.perform(get("/api/rollups/units/" + u.getId()).param("scope", "direct").param("source", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("all"))
                .andExpect(jsonPath("$.directTotals.overall.total_records").value(2))
                .andExpect(jsonPath("$.directTotals.overall.total_actual").value(800.0));
    }

    @Test
    void testMultiFileBatchImportHierarchyRollupAndIdempotency() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        UnitModel rootA = unitService.createUnit(UnitModel.builder()
                .name("Đơn vị A " + uid)
                .level(1)
                .build());

        // File 1: 8. Phu luc BTL thủ đô Hà Nội.xlsx -> Tầng 2: "8. Phụ lục BTL Thủ đô Hà Nội" -> Tầng 3: "Ban Chỉ huy PTKV 1 - Sóc Sơn"
        com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto file1 =
                com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto.builder()
                        .fileName("8. Phu luc BTL thủ đô Hà Nội.xlsx")
                        .fileHash("hash_btl_hn_" + uid)
                        .displayUnitName("8. Phụ lục BTL Thủ đô Hà Nội")
                        .parentUnitId(rootA.getId())
                        .parserMode("STANDARD_CTC")
                        .internalUnits(List.of(
                                com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchInternalUnitDto.builder()
                                        .name("Ban Chỉ huy PTKV 1 - Sóc Sơn")
                                        .build()
                        ))
                        .records(List.of(
                                PersonnelRecordModel.builder()
                                        .fullName("Hoàng Mạnh Tiến")
                                        .unitName("Ban Chỉ huy PTKV 1 - Sóc Sơn")
                                        .sheetType("I.1")
                                        .categoryCode("SQ")
                                        .source("excel")
                                        .actualTotal(100.0)
                                        .build(),
                                PersonnelRecordModel.builder()
                                        .fullName("Trần Văn Tâm")
                                        .unitName("Ban Chỉ huy PTKV 1 - Sóc Sơn")
                                        .sheetType("I.1")
                                        .categoryCode("SQ")
                                        .source("excel")
                                        .actualTotal(150.0)
                                        .build()
                        ))
                        .build();

        // File 2: 9. Phụ lục Quân Khu I.xlsx -> Tầng 2: "9. Phụ lục Quân khu I" -> Tầng 3: "Bộ CHQS tỉnh Cao Bằng"
        com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto file2 =
                com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto.builder()
                        .fileName("9. Phụ lục Quân Khu I.xlsx")
                        .fileHash("hash_qk1_" + uid)
                        .displayUnitName("9. Phụ lục Quân khu I")
                        .parentUnitId(rootA.getId())
                        .parserMode("STANDARD_CTC")
                        .internalUnits(List.of(
                                com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchInternalUnitDto.builder()
                                        .name("Bộ CHQS tỉnh Cao Bằng")
                                        .build()
                        ))
                        .records(List.of(
                                PersonnelRecordModel.builder()
                                        .fullName("Vũ Văn Toản")
                                        .unitName("Bộ CHQS tỉnh Cao Bằng")
                                        .sheetType("I.1")
                                        .categoryCode("SQ")
                                        .source("excel")
                                        .actualTotal(200.0)
                                        .build()
                        ))
                        .build();

        com.bqpvalidateexcel.storage.dto.BatchImportRequest batchReq =
                com.bqpvalidateexcel.storage.dto.BatchImportRequest.builder()
                        .commonParentUnitId(rootA.getId())
                        .files(List.of(file1, file2))
                        .build();

        // 1. Thực hiện commit-batch lần đầu
        com.bqpvalidateexcel.storage.dto.BatchImportResult batchResult = importService.commitBatch(batchReq);

        assertEquals(2, batchResult.getTotalFiles());
        assertEquals(2, batchResult.getSuccessCount());
        assertEquals(0, batchResult.getFailedCount());
        assertEquals(0, batchResult.getSkippedCount());

        // Kiểm tra phân cấp cây:
        // Đơn vị A (cấp 1) -> 8. Phụ lục BTL... (cấp 2) -> Ban Chỉ huy PTKV 1 - Sóc Sơn (cấp 3)
        List<UnitModel> childrenOfA = unitService.getAllUnits().stream()
                .filter(u -> rootA.getId().equals(u.getParentId()))
                .toList();
        assertEquals(2, childrenOfA.size(), "Đơn vị A phải có đúng 2 đơn vị con (2 đơn vị theo file)");

        UnitModel fileUnit1 = childrenOfA.stream()
                .filter(u -> u.getName().equals("8. Phụ lục BTL Thủ đô Hà Nội"))
                .findFirst().orElseThrow();
        assertEquals(2, fileUnit1.getLevel());

        UnitModel fileUnit2 = childrenOfA.stream()
                .filter(u -> u.getName().equals("9. Phụ lục Quân khu I"))
                .findFirst().orElseThrow();
        assertEquals(2, fileUnit2.getLevel());

        // Kiểm tra đơn vị tầng 3 dưới File Unit 1
        List<UnitModel> internalUnitsOfFile1 = unitService.getAllUnits().stream()
                .filter(u -> fileUnit1.getId().equals(u.getParentId()))
                .toList();
        assertEquals(1, internalUnitsOfFile1.size());
        assertEquals("Ban Chỉ huy PTKV 1 - Sóc Sơn", internalUnitsOfFile1.get(0).getName());
        assertEquals(3, internalUnitsOfFile1.get(0).getLevel());

        // 2. Kiểm tra Rollup tính toán tại Đơn vị A (toàn nhánh = tổng cả 2 file = 450.0 và 3 hồ sơ)
        Map<String, Object> rollupParent = rollupService.calculateRollup(rootA.getId(), "branch", "excel");
        assertNotNull(rollupParent.get("branchTotals"));
        Map<String, Object> parentBranchTotals = (Map<String, Object>) rollupParent.get("branchTotals");
        Map<String, Object> parentOverall = (Map<String, Object>) parentBranchTotals.get("overall");
        assertEquals(3, ((Number) parentOverall.get("total_records")).intValue());
        assertEquals(450.0, ((Number) parentOverall.get("total_actual")).doubleValue(), 0.001);

        // 3. Kiểm tra Rollup tại Đơn vị theo file 1 (chỉ gồm dữ liệu file 1 = 250.0 và 2 hồ sơ)
        Map<String, Object> rollupFile1 = rollupService.calculateRollup(fileUnit1.getId(), "branch", "excel");
        Map<String, Object> file1BranchTotals = (Map<String, Object>) rollupFile1.get("branchTotals");
        Map<String, Object> file1Overall = (Map<String, Object>) file1BranchTotals.get("overall");
        assertEquals(2, ((Number) file1Overall.get("total_records")).intValue());
        assertEquals(250.0, ((Number) file1Overall.get("total_actual")).doubleValue(), 0.001);

        // 4. Kiểm tra import lại cùng fileHash với duplicateAction = SKIP
        file1.setDuplicateAction("SKIP");
        com.bqpvalidateexcel.storage.dto.BatchImportRequest retryReq =
                com.bqpvalidateexcel.storage.dto.BatchImportRequest.builder()
                        .commonParentUnitId(rootA.getId())
                        .files(List.of(file1))
                        .build();

        com.bqpvalidateexcel.storage.dto.BatchImportResult retryResult = importService.commitBatch(retryReq);
        assertEquals(1, retryResult.getSkippedCount());
        assertEquals(0, retryResult.getSuccessCount());

        // Số hồ sơ và tổng tiền vẫn giữ nguyên không bị nhân đôi
        Map<String, Object> rollupParentAfterSkip = rollupService.calculateRollup(rootA.getId(), "branch", "excel");
        Map<String, Object> skipOverall = (Map<String, Object>) ((Map<String, Object>) rollupParentAfterSkip.get("branchTotals")).get("overall");
        assertEquals(3, ((Number) skipOverall.get("total_records")).intValue());

        // 5. Kiểm tra import lại cùng fileHash với duplicateAction = REPLACE
        file1.setDuplicateAction("REPLACE");
        file1.setRecords(List.of(
                PersonnelRecordModel.builder()
                        .fullName("Nguyễn Văn Mới Thay Thế")
                        .unitName("Ban Chỉ huy PTKV 1 - Sóc Sơn")
                        .sheetType("I.1")
                        .categoryCode("SQ")
                        .source("excel")
                        .actualTotal(500.0)
                        .build()
        ));
        com.bqpvalidateexcel.storage.dto.BatchImportRequest replaceReq =
                com.bqpvalidateexcel.storage.dto.BatchImportRequest.builder()
                        .commonParentUnitId(rootA.getId())
                        .files(List.of(file1))
                        .build();

        com.bqpvalidateexcel.storage.dto.BatchImportResult replaceResult = importService.commitBatch(replaceReq);
        assertEquals(1, replaceResult.getSuccessCount());

        // Tổng tiền tại Đơn vị A = 500.0 (file 1 đã thay thế) + 200.0 (file 2 giữ nguyên) = 700.0
        Map<String, Object> rollupParentAfterReplace = rollupService.calculateRollup(rootA.getId(), "branch", "excel");
        Map<String, Object> replaceOverall = (Map<String, Object>) ((Map<String, Object>) rollupParentAfterReplace.get("branchTotals")).get("overall");
        assertEquals(2, ((Number) replaceOverall.get("total_records")).intValue());
        assertEquals(700.0, ((Number) replaceOverall.get("total_actual")).doubleValue(), 0.001);
    }

    @Test
    void testMigrationV6_CanonicalUniqueIndex() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        UnitModel root = unitService.createUnit(UnitModel.builder()
                .name("Đơn vị Kiểm tra V6 " + uid)
                .level(1)
                .build());

        // Tạo đơn vị con 1
        unitService.createUnit(UnitModel.builder()
                .name("Ban Chỉ huy Quân sự huyện X " + uid)
                .parentId(root.getId())
                .level(2)
                .build());

        // Cố tình tạo đơn vị con 2 có cùng canonical name dưới cùng đơn vị cha
        assertThrows(Exception.class, () -> {
            unitService.createUnit(UnitModel.builder()
                    .name("Ban Chỉ Huy Quân Sự Huyện X " + uid)
                    .parentId(root.getId())
                    .level(2)
                    .build());
        });
    }

    @Test
    void testImportService_RejectsInactiveParent() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        UnitModel inactiveRoot = unitService.createUnit(UnitModel.builder()
                .name("Đơn vị Tạm Dừng " + uid)
                .level(1)
                .build());

        // Cập nhật trạng thái tạm dừng
        inactiveRoot.setIsActive(false);
        unitService.updateUnit(inactiveRoot.getId(), inactiveRoot);

        com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto file =
                com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto.builder()
                        .fileName("test_inactive.xlsx")
                        .fileHash("hash_inact_" + uid)
                        .displayUnitName("Đơn vị theo file " + uid)
                        .parentUnitId(inactiveRoot.getId())
                        .parserMode("STANDARD_CTC")
                        .internalUnits(List.of())
                        .records(List.of())
                        .build();

        com.bqpvalidateexcel.storage.dto.BatchImportRequest req =
                com.bqpvalidateexcel.storage.dto.BatchImportRequest.builder()
                        .commonParentUnitId(inactiveRoot.getId())
                        .files(List.of(file))
                        .build();

        com.bqpvalidateexcel.storage.dto.BatchImportResult res = importService.commitBatch(req);
        assertEquals(1, res.getFailedCount());
        assertTrue(res.getFileResults().get(0).getMessage().contains("không hoạt động"));
    }

    @Test
    void testMergeUnits_CycleCheckAndImportReassignment() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        UnitModel parent = unitService.createUnit(UnitModel.builder()
                .name("Cha Gộp " + uid)
                .level(1)
                .build());
        UnitModel child = unitService.createUnit(UnitModel.builder()
                .name("Con Gộp " + uid)
                .parentId(parent.getId())
                .level(2)
                .build());

        // 1. Kiểm tra chặn cycle: gộp cha vào con hoặc con vào cha có quan hệ tổ tiên
        assertThrows(IllegalArgumentException.class, () -> {
            unitService.mergeUnits(child.getId(), List.of(parent.getId()));
        });

        // 2. Tạo 2 đơn vị anh em: S1 và S2
        UnitModel s1 = unitService.createUnit(UnitModel.builder()
                .name("Đơn vị Đích " + uid)
                .parentId(parent.getId())
                .level(2)
                .build());
        UnitModel s2 = unitService.createUnit(UnitModel.builder()
                .name("Đơn vị Nguồn " + uid)
                .parentId(parent.getId())
                .level(2)
                .build());

        // Import tham chiếu tới s2
        com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto file =
                com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto.builder()
                        .fileName("test_merge.xlsx")
                        .fileHash("hash_merge_" + uid)
                        .displayUnitName("File Merge " + uid)
                        .parentUnitId(s2.getId())
                        .parserMode("STANDARD_CTC")
                        .internalUnits(List.of())
                        .records(List.of(PersonnelRecordModel.builder()
                                .fullName("Chiến sĩ Merge")
                                .unitName("File Merge " + uid)
                                .sheetType("I.1")
                                .categoryCode("SQ")
                                .source("excel")
                                .actualTotal(100.0)
                                .build()))
                        .build();

        com.bqpvalidateexcel.storage.dto.BatchImportResult batchRes = importService.commitBatch(
                com.bqpvalidateexcel.storage.dto.BatchImportRequest.builder()
                        .commonParentUnitId(s2.getId())
                        .files(List.of(file))
                        .build());
        assertEquals(1, batchRes.getSuccessCount());

        // Gộp s2 vào s1
        unitService.mergeUnits(s1.getId(), List.of(s2.getId()));

        // s2 phải đã bị xóa
        assertTrue(unitService.getUnitById(s2.getId()).isEmpty());

        // Import lúc trước tham chiếu tới s2 nay phải được chuyển sang s1
        var imp = importRepository.findByFileHash("hash_merge_" + uid).orElseThrow();
        assertEquals(s1.getId(), imp.getParentUnitId());
    }

    @Test
    void testDeleteUnit_ClearsImportReferences() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        UnitModel unit = unitService.createUnit(UnitModel.builder()
                .name("Đơn vị Cần Xóa " + uid)
                .level(1)
                .build());

        com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto file =
                com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto.builder()
                        .fileName("test_delete_ref.xlsx")
                        .fileHash("hash_del_ref_" + uid)
                        .displayUnitName("Đơn vị Cần Xóa " + uid)
                        .parentUnitId(unit.getId())
                        .parserMode("STANDARD_CTC")
                        .internalUnits(List.of())
                        .records(List.of())
                        .build();

        importService.commitBatch(com.bqpvalidateexcel.storage.dto.BatchImportRequest.builder()
                .commonParentUnitId(unit.getId())
                .files(List.of(file))
                .build());

        // Xóa đơn vị - không bị lỗi FK vì clearUnitReferences đã giải phóng
        assertDoesNotThrow(() -> unitService.deleteUnit(unit.getId()));
        assertTrue(unitService.getUnitById(unit.getId()).isEmpty());

        var imp = importRepository.findByFileHash("hash_del_ref_" + uid).orElseThrow();
        assertNull(imp.getParentUnitId());
    }

    @Test
    void testDeleteRecords_SyncsImportStatusToCleared() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        UnitModel root = unitService.createUnit(UnitModel.builder()
                .name("Đơn vị Test Clear " + uid)
                .level(1)
                .build());

        com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto file =
                com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto.builder()
                        .fileName("test_clear.xlsx")
                        .fileHash("hash_clear_" + uid)
                        .displayUnitName("File Clear " + uid)
                        .parentUnitId(root.getId())
                        .parserMode("STANDARD_CTC")
                        .internalUnits(List.of())
                        .records(List.of(PersonnelRecordModel.builder()
                                .fullName("Chiến sĩ Clear")
                                .unitName("File Clear " + uid)
                                .sheetType("I.1")
                                .categoryCode("SQ")
                                .source("excel")
                                .actualTotal(100.0)
                                .build()))
                        .build();

        importService.commitBatch(com.bqpvalidateexcel.storage.dto.BatchImportRequest.builder()
                .commonParentUnitId(root.getId())
                .files(List.of(file))
                .build());

        var impBefore = importRepository.findByFileHash("hash_clear_" + uid).orElseThrow();
        assertEquals("SUCCESS", impBefore.getStatus());
        assertEquals(1, impBefore.getAcceptedRows());
        assertEquals(1, impBefore.getCurrentRecordCount());

        // Xóa toàn bộ hồ sơ excel
        recordService.deleteBySource("excel");

        var impAfter = importRepository.findByFileHash("hash_clear_" + uid).orElseThrow();
        assertEquals("CLEARED", impAfter.getStatus());
        assertEquals(0, impAfter.getAcceptedRows());
        assertEquals(0, impAfter.getCurrentRecordCount());
    }

    @Test
    void testCommitAtomicBatch_SuccessAndCommonUnitTree() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        UnitModel root = unitService.createUnit(UnitModel.builder()
                .name("Quân khu Atomic " + uid)
                .level(1)
                .isActive(true)
                .isPreset(false)
                .build());

        var fileValidated = com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto.builder()
                .fileName("test_dual_" + uid + ".xlsx")
                .fileHash("hash_dual_val_" + uid)
                .displayUnitName("Sư đoàn 324 " + uid)
                .parentUnitId(root.getId())
                .internalUnits(List.of(new com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchInternalUnitDto("Ban Tác chiến", "TC")))
                .records(List.of(
                        PersonnelRecordModel.builder()
                                .fullName("Nguyễn Văn A " + uid)
                                .unitName("Ban Tác chiến")
                                .sheetType("I.1")
                                .source("validated")
                                .actualTotal(100.0)
                                .calculatedTotal(110.0)
                                .difference(10.0)
                                .build(),
                        PersonnelRecordModel.builder()
                                .fullName("Trần Văn B " + uid)
                                .unitName("Sư đoàn 324 " + uid)
                                .sheetType("I.1")
                                .source("validated")
                                .actualTotal(200.0)
                                .calculatedTotal(200.0)
                                .difference(0.0)
                                .build()
                ))
                .build();

        var fileRaw = com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto.builder()
                .fileName("test_dual_" + uid + ".xlsx [Gốc]")
                .fileHash("hash_dual_val_" + uid + "_EXCEL")
                .displayUnitName("Sư đoàn 324 " + uid)
                .parentUnitId(root.getId())
                .internalUnits(List.of(new com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchInternalUnitDto("Ban Tác chiến", "TC")))
                .records(List.of(
                        PersonnelRecordModel.builder()
                                .fullName("Nguyễn Văn A " + uid)
                                .unitName("Ban Tác chiến")
                                .sheetType("I.1")
                                .source("excel")
                                .actualTotal(100.0)
                                .calculatedTotal(0.0)
                                .difference(0.0)
                                .build(),
                        PersonnelRecordModel.builder()
                                .fullName("Trần Văn B " + uid)
                                .unitName("Sư đoàn 324 " + uid)
                                .sheetType("I.1")
                                .source("excel")
                                .actualTotal(200.0)
                                .calculatedTotal(0.0)
                                .difference(0.0)
                                .build()
                ))
                .build();

        var request = com.bqpvalidateexcel.storage.dto.BatchImportRequest.builder()
                .atomic(true)
                .operationType("DUAL_SAVE_VALIDATION")
                .commonParentUnitId(root.getId())
                .files(List.of(fileValidated, fileRaw))
                .build();

        var result = importService.commitBatch(request);

        assertTrue(result.getAtomic());
        assertTrue(result.getCommitted());
        assertFalse(result.getRolledBack());
        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getFailedCount());

        // Kiểm tra dữ liệu được lưu vào cả 2 nguồn
        var valRecs = recordService.findAllBySource("validated");
        var excRecs = recordService.findAllBySource("excel");
        assertEquals(2, valRecs.size());
        assertEquals(2, excRecs.size());

        // Kiểm tra hai bản ghi tương ứng có chung unit_id (không nhân đôi đơn vị)
        var valA = valRecs.stream().filter(r -> r.getFullName().contains("Nguyễn Văn A")).findFirst().orElseThrow();
        var excA = excRecs.stream().filter(r -> r.getFullName().contains("Nguyễn Văn A")).findFirst().orElseThrow();
        assertEquals(valA.getUnitId(), excA.getUnitId(), "RAW và Validated của cùng đối tượng phải có cùng unit_id");

        var valB = valRecs.stream().filter(r -> r.getFullName().contains("Trần Văn B")).findFirst().orElseThrow();
        var excB = excRecs.stream().filter(r -> r.getFullName().contains("Trần Văn B")).findFirst().orElseThrow();
        assertEquals(valB.getUnitId(), excB.getUnitId(), "RAW và Validated của cùng đối tượng phải có cùng unit_id");
    }

    @Test
    void testCommitAtomicBatch_RollbackOnCountMismatchOrError() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        UnitModel root = unitService.createUnit(UnitModel.builder()
                .name("Quân khu Rollback " + uid)
                .level(1)
                .isActive(true)
                .isPreset(false)
                .build());

        var fileValidated = com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto.builder()
                .fileName("test_rollback_" + uid + ".xlsx")
                .fileHash("hash_rb_val_" + uid)
                .displayUnitName("Sư đoàn Rollback " + uid)
                .parentUnitId(root.getId())
                .records(List.of(
                        PersonnelRecordModel.builder()
                                .fullName("Nguyễn Văn X " + uid)
                                .sheetType("I.1")
                                .source("validated")
                                .actualTotal(100.0)
                                .build()
                ))
                .build();

        // File RAW có 0 bản ghi (lệch số lượng bản ghi)
        var fileRaw = com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto.builder()
                .fileName("test_rollback_" + uid + ".xlsx [Gốc]")
                .fileHash("hash_rb_raw_" + uid)
                .displayUnitName("Sư đoàn Rollback " + uid)
                .parentUnitId(root.getId())
                .records(List.of())
                .build();

        var request = com.bqpvalidateexcel.storage.dto.BatchImportRequest.builder()
                .atomic(true)
                .operationType("DUAL_SAVE_VALIDATION")
                .commonParentUnitId(root.getId())
                .files(List.of(fileValidated, fileRaw))
                .build();

        var result = importService.commitBatch(request);

        assertTrue(result.getAtomic());
        assertFalse(result.getCommitted());
        assertTrue(result.getRolledBack());
        assertEquals(2, result.getFailedCount());

        // Kiểm tra không có bất kỳ hồ sơ nào lọt vào CSDL sau rollback
        var valRecs = recordService.findAllBySource("validated");
        var excRecs = recordService.findAllBySource("excel");
        assertEquals(0, valRecs.size(), "Không được có bản ghi validated nào tồn tại sau khi giao dịch rollback");
        assertEquals(0, excRecs.size(), "Không được có bản ghi excel nào tồn tại sau khi giao dịch rollback");
    }

    @Test
    void testCommitAtomicBatch_Replace() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        UnitModel root = unitService.createUnit(UnitModel.builder()
                .name("Quân khu Replace " + uid)
                .level(1)
                .isActive(true)
                .isPreset(false)
                .build());

        var fileValidated = com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto.builder()
                .fileName("test_rep_" + uid + ".xlsx")
                .fileHash("hash_rep_val_" + uid)
                .displayUnitName("Sư đoàn Replace " + uid)
                .parentUnitId(root.getId())
                .duplicateAction("NEW")
                .records(List.of(
                        PersonnelRecordModel.builder()
                                .fullName("Lê Văn C " + uid)
                                .sheetType("I.1")
                                .source("validated")
                                .actualTotal(500.0)
                                .build()
                ))
                .build();

        var fileRaw = com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto.builder()
                .fileName("test_rep_" + uid + ".xlsx [Gốc]")
                .fileHash("hash_rep_val_" + uid + "_EXCEL")
                .displayUnitName("Sư đoàn Replace " + uid)
                .parentUnitId(root.getId())
                .duplicateAction("NEW")
                .records(List.of(
                        PersonnelRecordModel.builder()
                                .fullName("Lê Văn C " + uid)
                                .sheetType("I.1")
                                .source("excel")
                                .actualTotal(500.0)
                                .build()
                ))
                .build();

        var req1 = com.bqpvalidateexcel.storage.dto.BatchImportRequest.builder()
                .atomic(true)
                .commonParentUnitId(root.getId())
                .files(List.of(fileValidated, fileRaw))
                .build();

        var res1 = importService.commitBatch(req1);
        assertTrue(res1.getCommitted());
        assertEquals(1, recordService.findAllBySource("validated").size());
        assertEquals(1, recordService.findAllBySource("excel").size());

        // Lần 2: Nhập lại cùng file nhưng với duplicateAction = REPLACE
        fileValidated.setDuplicateAction("REPLACE");
        fileRaw.setDuplicateAction("REPLACE");
        var req2 = com.bqpvalidateexcel.storage.dto.BatchImportRequest.builder()
                .atomic(true)
                .commonParentUnitId(root.getId())
                .files(List.of(fileValidated, fileRaw))
                .build();

        var res2 = importService.commitBatch(req2);
        assertTrue(res2.getCommitted());
        assertEquals(1, recordService.findAllBySource("validated").size(), "Số bản ghi validated sau REPLACE không bị nhân đôi");
        assertEquals(1, recordService.findAllBySource("excel").size(), "Số bản ghi excel sau REPLACE không bị nhân đôi");
    }

    @Test
    void testCommitBatch_ContractWithBrowserJson() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        UnitModel root = unitService.createUnit(UnitModel.builder()
                .name("Quân khu Browser " + uid)
                .level(1)
                .isActive(true)
                .isPreset(false)
                .build());

        // Payload mô phỏng chính xác những gì trình duyệt gửi:
        // - "unit" thay vì "unitName"
        // - "demobilizationDate" thay vì "retirementDate"
        // - "recruitmentDate" thay vì "mergerDate"
        // - "errorDetails" dạng mảng chuỗi ["Cột 10: Sai dữ liệu"]
        String jsonPayload = "{\n" +
                "  \"commonParentUnitId\": \"" + root.getId() + "\",\n" +
                "  \"atomic\": true,\n" +
                "  \"operationType\": \"DUAL_SAVE_VALIDATION\",\n" +
                "  \"files\": [\n" +
                "    {\n" +
                "      \"fileName\": \"browser_val_" + uid + ".xlsx\",\n" +
                "      \"fileHash\": \"hash_b_val_" + uid + "\",\n" +
                "      \"displayUnitName\": \"Sư đoàn Browser " + uid + "\",\n" +
                "      \"parentUnitId\": \"" + root.getId() + "\",\n" +
                "      \"duplicateAction\": \"NEW\",\n" +
                "      \"internalUnits\": [{\"name\": \"Phòng Kế hoạch " + uid + "\", \"code\": \"PKH\"}],\n" +
                "      \"records\": [\n" +
                "        {\n" +
                "          \"id\": \"val_" + uid + "\",\n" +
                "          \"source\": \"validated\",\n" +
                "          \"sheetType\": \"I.1\",\n" +
                "          \"fullName\": \"Trần Văn Browser " + uid + "\",\n" +
                "          \"unit\": \"Phòng Kế hoạch " + uid + "\",\n" +
                "          \"demobilizationDate\": \"2026-05-01\",\n" +
                "          \"recruitmentDate\": \"2021-03-01\",\n" +
                "          \"monthlySalary\": 15000000.0,\n" +
                "          \"actualTotal\": 200000000.0,\n" +
                "          \"calculatedTotal\": 210000000.0,\n" +
                "          \"difference\": 10000000.0,\n" +
                "          \"hasErrors\": true,\n" +
                "          \"errorDetails\": [\"Cột 10: Sai dữ liệu lương\", \"Cột 15: Chênh lệch quy định\"]\n" +
                "        }\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"fileName\": \"browser_val_" + uid + ".xlsx [Gốc]\",\n" +
                "      \"fileHash\": \"hash_b_val_" + uid + "_EXCEL\",\n" +
                "      \"displayUnitName\": \"Sư đoàn Browser " + uid + "\",\n" +
                "      \"parentUnitId\": \"" + root.getId() + "\",\n" +
                "      \"duplicateAction\": \"NEW\",\n" +
                "      \"internalUnits\": [{\"name\": \"Phòng Kế hoạch " + uid + "\", \"code\": \"PKH\"}],\n" +
                "      \"records\": [\n" +
                "        {\n" +
                "          \"id\": \"excel_" + uid + "\",\n" +
                "          \"source\": \"excel\",\n" +
                "          \"sheetType\": \"I.1\",\n" +
                "          \"fullName\": \"Trần Văn Browser " + uid + "\",\n" +
                "          \"unit\": \"Phòng Kế hoạch " + uid + "\",\n" +
                "          \"demobilizationDate\": \"2026-05-01\",\n" +
                "          \"recruitmentDate\": \"2021-03-01\",\n" +
                "          \"monthlySalary\": 15000000.0,\n" +
                "          \"actualTotal\": 200000000.0,\n" +
                "          \"calculatedTotal\": 0.0,\n" +
                "          \"difference\": 0.0,\n" +
                "          \"hasErrors\": false,\n" +
                "          \"errorDetails\": []\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        mockMvc.perform(post("/api/imports/commit-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committed").value(true))
                .andExpect(jsonPath("$.totalFiles").value(2))
                .andExpect(jsonPath("$.successCount").value(2));

        // Kiểm tra bản ghi validated được lưu đầy đủ không bị mất trường
        List<PersonnelRecordModel> valList = recordService.findAllBySource("validated");
        assertEquals(1, valList.size());
        PersonnelRecordModel valRec = valList.get(0);
        assertEquals("Phòng Kế hoạch " + uid, valRec.getUnitName(), "Tên đơn vị phải được ánh xạ đúng từ 'unit'");
        assertEquals("2026-05-01", valRec.getRetirementDate(), "Ngày nghỉ phải được ánh xạ đúng từ 'demobilizationDate'");
        assertEquals("2021-03-01", valRec.getMergerDate(), "Ngày sáp nhập phải được ánh xạ đúng từ 'recruitmentDate'");
        assertEquals(10000000.0, valRec.getDifference(), 0.001);

        // Kiểm tra bản ghi RAW: difference phải giữ 0.0, KHÔNG bị đổi thành -actualTotal
        List<PersonnelRecordModel> rawList = recordService.findAllBySource("excel");
        assertEquals(1, rawList.size());
        PersonnelRecordModel rawRec = rawList.get(0);
        assertEquals("Phòng Kế hoạch " + uid, rawRec.getUnitName());
        assertEquals("2026-05-01", rawRec.getRetirementDate());
        assertEquals(0.0, rawRec.getCalculatedTotal(), 0.001);
        assertEquals(0.0, rawRec.getDifference(), 0.001, "Difference của RAW phải giữ 0.0, tuyệt đối không bị tính thành -actualTotal");
    }

    @Test
    void testCommitAtomicBatch_PairConflict_RejectHalfPair() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        UnitModel root = unitService.createUnit(UnitModel.builder()
                .name("Quân khu PairConflict " + uid)
                .level(1)
                .isActive(true)
                .isPreset(false)
                .build());

        var fileVal = com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto.builder()
                .fileName("test_conflict_" + uid + ".xlsx")
                .fileHash("hash_conflict_val_" + uid)
                .displayUnitName("Sư đoàn Conflict " + uid)
                .parentUnitId(root.getId())
                .duplicateAction("NEW")
                .records(List.of(
                        PersonnelRecordModel.builder()
                                .fullName("Người Val " + uid)
                                .sheetType("I.1")
                                .source("validated")
                                .actualTotal(500.0)
                                .build()
                ))
                .build();

        var fileRaw = com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto.builder()
                .fileName("test_conflict_" + uid + ".xlsx [Gốc]")
                .fileHash("hash_conflict_raw_" + uid)
                .displayUnitName("Sư đoàn Conflict " + uid)
                .parentUnitId(root.getId())
                .duplicateAction("NEW")
                .records(List.of(
                        PersonnelRecordModel.builder()
                                .fullName("Người Val " + uid)
                                .sheetType("I.1")
                                .source("excel")
                                .actualTotal(500.0)
                                .build()
                ))
                .build();

        // Lưu trước 1 file (mô phỏng dữ liệu cũ chỉ có 1 nửa cặp)
        var singleReq = com.bqpvalidateexcel.storage.dto.BatchImportRequest.builder()
                .atomic(false)
                .commonParentUnitId(root.getId())
                .files(List.of(fileVal))
                .build();
        importService.commitBatch(singleReq);
        assertEquals(1, recordService.findAllBySource("validated").size());
        assertEquals(0, recordService.findAllBySource("excel").size());

        // Thử chạy commitAtomicBatch với SKIP: 1 nửa đã có, 1 nửa chưa -> Bắt buộc ném PAIR_CONFLICT
        var atomicSkipReq = com.bqpvalidateexcel.storage.dto.BatchImportRequest.builder()
                .atomic(true)
                .operationType("DUAL_SAVE_VALIDATION")
                .commonParentUnitId(root.getId())
                .files(List.of(fileVal, fileRaw))
                .build();
        fileVal.setDuplicateAction("SKIP");
        fileRaw.setDuplicateAction("SKIP");

        var result = importService.commitBatch(atomicSkipReq);
        assertFalse(result.getCommitted(), "Giao dịch không được commit khi có xung đột cặp dữ liệu");
        assertTrue(result.getRolledBack());
        assertTrue(result.getMessage().contains("PAIR_CONFLICT"));
        // Đảm bảo không ghi thêm nửa RAW vào CSDL
        assertEquals(0, recordService.findAllBySource("excel").size());
    }

    @Test
    void testCommitAtomicBatch_PairSkip_BothExist() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        UnitModel root = unitService.createUnit(UnitModel.builder()
                .name("Quân khu PairSkip " + uid)
                .level(1)
                .isActive(true)
                .isPreset(false)
                .build());

        var fileVal = com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto.builder()
                .fileName("test_skip_" + uid + ".xlsx")
                .fileHash("hash_skip_val_" + uid)
                .displayUnitName("Sư đoàn Skip " + uid)
                .parentUnitId(root.getId())
                .duplicateAction("NEW")
                .records(List.of(PersonnelRecordModel.builder().fullName("A " + uid).sheetType("I.1").source("validated").actualTotal(100.0).build()))
                .build();

        var fileRaw = com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto.builder()
                .fileName("test_skip_" + uid + ".xlsx [Gốc]")
                .fileHash("hash_skip_val_" + uid + "_EXCEL")
                .displayUnitName("Sư đoàn Skip " + uid)
                .parentUnitId(root.getId())
                .duplicateAction("NEW")
                .records(List.of(PersonnelRecordModel.builder().fullName("A " + uid).sheetType("I.1").source("excel").actualTotal(100.0).build()))
                .build();

        var req1 = com.bqpvalidateexcel.storage.dto.BatchImportRequest.builder()
                .atomic(true)
                .commonParentUnitId(root.getId())
                .files(List.of(fileVal, fileRaw))
                .build();
        var res1 = importService.commitBatch(req1);
        assertTrue(res1.getCommitted());

        // Lần 2: Cả 2 cùng tồn tại và chọn SKIP
        fileVal.setDuplicateAction("SKIP");
        fileRaw.setDuplicateAction("SKIP");
        var req2 = com.bqpvalidateexcel.storage.dto.BatchImportRequest.builder()
                .atomic(true)
                .commonParentUnitId(root.getId())
                .files(List.of(fileVal, fileRaw))
                .build();
        var res2 = importService.commitBatch(req2);

        assertFalse(res2.getCommitted(), "Khi cả cặp đã tồn tại và chọn SKIP thì committed phải là false");
        assertEquals(2, res2.getSkippedCount());
        assertEquals(0, res2.getSuccessCount());
        assertEquals(1, recordService.findAllBySource("validated").size());
        assertEquals(1, recordService.findAllBySource("excel").size());
    }

    @Test
    void testInventoryReport_BusinessIntegrity() {
        Map<String, Object> report = unitService.getInventoryReport();
        assertNotNull(report);
        assertTrue(report.containsKey("businessIntegrity"));
        Map<String, Object> bi = (Map<String, Object>) report.get("businessIntegrity");
        assertNotNull(bi);
        assertTrue(bi.containsKey("activeRootCount"));
        assertTrue(bi.containsKey("activeUnitsWithInactiveAncestor"));
        assertTrue(bi.containsKey("importsWithMissingRecords"));
        assertTrue(bi.containsKey("importsReferencingInactiveUnits"));
        assertTrue(bi.containsKey("importsAcceptedVsActualMismatch"));
    }

    @Test
    void testSyncUnits_EmptyPayloadRejection_P1_01() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        unitService.createUnit(UnitModel.builder()
                .name("Đơn vị bảo vệ " + uid)
                .level(1)
                .isActive(true)
                .isPreset(false)
                .build());

        // Gọi syncUnits với danh sách rỗng khi hệ thống đang có đơn vị -> Bắt buộc ném IllegalArgumentException
        IllegalArgumentException exEmpty = assertThrows(IllegalArgumentException.class, () -> {
            unitService.syncUnits(Collections.emptyList());
        });
        assertTrue(exEmpty.getMessage().contains("Payload đồng bộ đơn vị không được rỗng"));

        // Gọi syncUnits với null -> Bắt buộc ném IllegalArgumentException
        IllegalArgumentException exNull = assertThrows(IllegalArgumentException.class, () -> {
            unitService.syncUnits(null);
        });
        assertTrue(exNull.getMessage().contains("Payload đồng bộ đơn vị không được rỗng"));
    }

    @Test
    void testActiveUnitUnderInactiveParent_Rejection_P2_03() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        UnitModel inactiveRoot = unitService.createUnit(UnitModel.builder()
                .name("Gốc tạm dừng " + uid)
                .level(1)
                .isActive(false)
                .isPreset(false)
                .build());

        // 1. Thử tạo đơn vị con active dưới cha inactive -> Ném IllegalArgumentException
        IllegalArgumentException exCreate = assertThrows(IllegalArgumentException.class, () -> {
            unitService.createUnit(UnitModel.builder()
                    .name("Con hoạt động " + uid)
                    .parentId(inactiveRoot.getId())
                    .level(2)
                    .isActive(true)
                    .build());
        });
        assertTrue(exCreate.getMessage().contains("tạm dừng hoạt động"));

        // 2. Thử batchSave đơn vị con active dưới cha inactive -> Ném IllegalArgumentException
        IllegalArgumentException exBatch = assertThrows(IllegalArgumentException.class, () -> {
            unitService.batchSave(List.of(UnitModel.builder()
                    .name("Con batch " + uid)
                    .parentId(inactiveRoot.getId())
                    .level(2)
                    .isActive(true)
                    .build()));
        });
        assertTrue(exBatch.getMessage().contains("tạm dừng hoạt động"));
    }

    @Test
    void testCommitAtomicBatch_PairIntegrity_MismatchedDuplicateActionOrRecords_P1_02() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        UnitModel root = unitService.createUnit(UnitModel.builder()
                .name("Quân khu PairIntegrity " + uid)
                .level(1)
                .isActive(true)
                .isPreset(false)
                .build());

        var fileVal = com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto.builder()
                .fileName("pair_test_" + uid + ".xlsx")
                .fileHash("hash_pair_val_" + uid)
                .displayUnitName("Sư đoàn 1 " + uid)
                .parentUnitId(root.getId())
                .duplicateAction("REPLACE")
                .records(List.of(PersonnelRecordModel.builder().sourceRow(5).fullName("Nguyễn Văn A " + uid).sheetType("I.1").source("validated").build()))
                .build();

        var fileRawMismatchedAction = com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto.builder()
                .fileName("pair_test_" + uid + ".xlsx [Gốc]")
                .fileHash("hash_pair_val_" + uid + "_EXCEL")
                .displayUnitName("Sư đoàn 1 " + uid)
                .parentUnitId(root.getId())
                .duplicateAction("SKIP") // Lệch với fileVal là REPLACE
                .records(List.of(PersonnelRecordModel.builder().sourceRow(5).fullName("Nguyễn Văn A " + uid).sheetType("I.1").source("excel").build()))
                .build();

        // 1. Lệch duplicateAction giữa 2 nửa cặp -> Bị từ chối và rollback toàn bộ
        var reqMismatchedAction = com.bqpvalidateexcel.storage.dto.BatchImportRequest.builder()
                .atomic(true)
                .commonParentUnitId(root.getId())
                .files(List.of(fileVal, fileRawMismatchedAction))
                .build();
        var resAction = importService.commitBatch(reqMismatchedAction);
        assertFalse(resAction.getCommitted(), "Lệch duplicateAction không được phép commit");
        assertTrue(resAction.getRolledBack(), "Giao dịch phải được rollback toàn bộ");
        assertTrue(resAction.getMessage().contains("chính sách trùng lặp"),
                "Thông báo lỗi phải nêu rõ lỗi chính sách trùng lặp: " + resAction.getMessage());

        // 2. Lệch danh tính bản ghi giữa 2 nửa cặp (khác fullName hoặc sourceRow) -> Bị từ chối và rollback toàn bộ
        var fileRawMismatchedRecord = com.bqpvalidateexcel.storage.dto.BatchImportRequest.BatchFileDto.builder()
                .fileName("pair_test_" + uid + ".xlsx [Gốc]")
                .fileHash("hash_pair_val_" + uid + "_EXCEL")
                .displayUnitName("Sư đoàn 1 " + uid)
                .parentUnitId(root.getId())
                .duplicateAction("REPLACE")
                .records(List.of(PersonnelRecordModel.builder().sourceRow(6).fullName("Trần Văn B " + uid).sheetType("I.1").source("excel").build()))
                .build();
        var reqMismatchedRecord = com.bqpvalidateexcel.storage.dto.BatchImportRequest.builder()
                .atomic(true)
                .commonParentUnitId(root.getId())
                .files(List.of(fileVal, fileRawMismatchedRecord))
                .build();
        var resRecord = importService.commitBatch(reqMismatchedRecord);
        assertFalse(resRecord.getCommitted(), "Lệch danh tính bản ghi không được phép commit");
        assertTrue(resRecord.getRolledBack(), "Giao dịch phải được rollback toàn bộ");
        assertTrue(resRecord.getMessage().contains("không tương ứng cùng một tập đối tượng"),
                "Thông báo lỗi phải cảnh báo lệch đối tượng: " + resRecord.getMessage());
    }

    @Test
    void testExportSnapshotSession_ValueImmutability_P1_03() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        UnitModel root = unitService.createUnit(UnitModel.builder()
                .name("Đơn vị Snapshot " + uid)
                .level(1)
                .isActive(true)
                .isPreset(false)
                .build());

        // Tạo 2 hồ sơ ban đầu
        PersonnelRecordModel r1 = PersonnelRecordModel.builder()
                .id("rec_snap_" + uid + "_1")
                .source("validated")
                .unitId(root.getId())
                .fullName("Quân nhân Gốc 1")
                .sheetType("I.1")
                .actualTotal(1000000.0)
                .build();
        PersonnelRecordModel r2 = PersonnelRecordModel.builder()
                .id("rec_snap_" + uid + "_2")
                .source("validated")
                .unitId(root.getId())
                .fullName("Quân nhân Gốc 2")
                .sheetType("I.1")
                .actualTotal(2000000.0)
                .build();
        recordService.saveBatch(List.of(r1, r2));

        // 1. Tạo phiên xuất dữ liệu (Snapshot)
        Map<String, Object> session = recordService.createExportSession("validated", root.getId(), "self", "I.1", null, null, null);
        String sessionId = (String) session.get("sessionId");
        assertNotNull(sessionId);

        // 2. Thay đổi dữ liệu trong database SQLite SAU KHI phiên đã được tạo
        // - Cập nhật họ tên và số tiền của r1
        r1.setFullName("Quân nhân ĐÃ BỊ SỬA LÉN");
        r1.setActualTotal(9999999.0);
        recordService.saveRecord(r1);

        // - Xóa hoàn toàn r2 khỏi database
        recordService.deleteById(r2.getId());

        // - Thêm hồ sơ r3 mới toanh vào database
        PersonnelRecordModel r3 = PersonnelRecordModel.builder()
                .id("rec_snap_" + uid + "_3")
                .source("validated")
                .unitId(root.getId())
                .fullName("Quân nhân Mới Thêm")
                .sheetType("I.1")
                .build();
        recordService.saveRecord(r3);

        // 3. Đọc dữ liệu từ phiên Snapshot
        Map<String, Object> pageData = recordService.getExportSessionPage(sessionId, 0, 10, false);
        List<PersonnelRecordModel> items = (List<PersonnelRecordModel>) pageData.get("items");

        // Khẳng định giá trị phiên xuất hoàn toàn bất biến theo đúng ảnh chụp lúc tạo phiên:
        assertEquals(2, items.size(), "Phiên snapshot phải giữ nguyên đúng 2 hồ sơ ban đầu");
        assertEquals("Quân nhân Gốc 1", items.get(0).getFullName(), "Tên không được bị thay đổi theo DB");
        assertEquals(1000000.0, items.get(0).getActualTotal(), "Số tiền không được bị thay đổi theo DB");
        assertEquals("Quân nhân Gốc 2", items.get(1).getFullName(), "Hồ sơ bị xóa trong DB vẫn phải còn trong snapshot xuất");

        // Đóng phiên
        recordService.closeExportSession(sessionId);
    }

    private String getMigrationChecksum(String filename) {
        try {
            org.springframework.core.io.support.PathMatchingResourcePatternResolver resolver =
                    new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
            org.springframework.core.io.Resource r = resolver.getResource("classpath:db/migration/" + filename);
            byte[] bytes = r.getInputStream().readAllBytes();
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

