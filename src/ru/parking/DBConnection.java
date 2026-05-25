package ru.parking;

import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;

public class DBConnection {
    public static Connection getConnection() throws Exception {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream("config.properties")) {
            props.load(in);
        }
        
        String dbType = props.getProperty("current.db");
        String url = props.getProperty(dbType + ".url");
        String user = props.getProperty(dbType + ".user");
        String pass = props.getProperty(dbType + ".password");
        
        if (dbType.equals("mssql")) {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } else {
            Class.forName("org.postgresql.Driver");
        }

        Connection conn = DriverManager.getConnection(url, user, pass);

        // Специальная настройка для облачного Postgres Pro
        if (dbType.equals("pg")) {
            try (Statement st = conn.createStatement()) {
                st.execute("SET search_path TO obuhova_ss");
            }
        }

        return conn;
    }
}