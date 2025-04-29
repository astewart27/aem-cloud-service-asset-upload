package com.slalom.playground.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Configuration
@Data
public class AemConfig {
    
    @Value("${config.authorUrl}")
    public String authorUrl;
}
