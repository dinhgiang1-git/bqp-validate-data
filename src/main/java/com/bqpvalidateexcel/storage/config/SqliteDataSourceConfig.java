package com.bqpvalidateexcel.storage.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class SqliteDataSourceConfig {

    private final DataDirectoryResolver dataDirectoryResolver;

    public SqliteDataSourceConfig(DataDirectoryResolver dataDirectoryResolver) {
        this.dataDirectoryResolver = dataDirectoryResolver;
    }

    @Bean
    @Primary
    public DataSource dataSource() {
        String dbPath = dataDirectoryResolver.getDatabaseFile().toAbsolutePath().toString().replace('\\', '/');
        String jdbcUrl = "jdbc:sqlite:" + dbPath;

        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl(jdbcUrl);
        config.setPoolName("BqpSqlitePool");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(60000);
        config.setMaxLifetime(300000);
        config.setConnectionInitSql(
                "PRAGMA foreign_keys = ON; " +
                "PRAGMA journal_mode = WAL; " +
                "PRAGMA synchronous = NORMAL; " +
                "PRAGMA busy_timeout = 5000;"
        );

        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper();
    }
}
