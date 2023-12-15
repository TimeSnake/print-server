package de.timesnake.web.printserver.util;

import de.timesnake.web.printserver.Application;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Service
public class Config {

  private final Properties properties;

  public Config() {
    String fileName = "config.cfg";
    properties = new Properties();
    try {
      InputStream is = new FileInputStream(fileName);
      properties.load(is);
    } catch (FileNotFoundException e) {
      Application.getLogger().warning("Config file '" + fileName + "' does not exists");
    } catch (IOException e) {
      Application.getLogger().warning("Failed to read config file: " + e.getMessage());
    }
  }

  public String getDatabaseUser() {
    return this.properties.getProperty("database.user", "root");
  }

  public String getDatabasePassword() {
    return this.properties.getProperty("database.password", "insecure-password");
  }

  public String getDatabaseUrl() {
    return this.properties.getProperty("database.url", "jdbc:mariadb://localhost:3306/print_server?createDatabaseIfNotExist=true");
  }

  public int getMaxFileSizeInMB() {
    return Integer.parseInt(this.properties.getProperty("maxFileSize", "100"));
  }
}

