package com.firebird_data_extractor.www.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.firebirdsql.ds.FBSimpleDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.nio.file.Path;

@Configuration
public class DatabaseConfig {

    public record ServerConfig(
        String databaseFile,
        String user,
        String password,
        String host,
        Integer port
    ) {}

    @Bean
    public DataSource dataSource() throws Exception {

        Path configPath = Path.of(
            System.getProperty("user.dir"),
            "server-config.json"
        );

        ObjectMapper mapper = new ObjectMapper();

        ServerConfig config = mapper.readValue(
            configPath.toFile(),
            ServerConfig.class
        );

        FBSimpleDataSource fb = new FBSimpleDataSource();

        fb.setServerName(config.host());
        fb.setPortNumber(config.port());

        fb.setDatabase(config.databaseFile());


        fb.setUser(config.user());
        fb.setPassword(config.password());

        HikariDataSource hikari = new HikariDataSource();

        hikari.setDataSource(fb);

        hikari.setMaximumPoolSize(5);

        return hikari;
    }
}