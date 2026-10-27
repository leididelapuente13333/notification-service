package com.jcaa.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NotificationServiceApplication {

  public static void main(final String[] args) {
    SpringApplication.run(NotificationServiceApplication.class, args);
  }

}
