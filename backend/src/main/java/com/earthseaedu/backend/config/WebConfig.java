package com.earthseaedu.backend.config;

import cn.hutool.core.text.CharSequenceUtil;
import com.earthseaedu.backend.support.StoragePathSupport;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final EarthSeaProperties properties;
    private final StoragePathSupport storagePathSupport;

    public WebConfig(EarthSeaProperties properties, StoragePathSupport storagePathSupport) {
        this.properties = properties;
        this.storagePathSupport = storagePathSupport;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> allowedOrigins = Arrays.stream(
                CharSequenceUtil.nullToDefault(properties.getBackendCorsOrigins(), "*").split(",")
            )
            .map(String::trim)
            .filter(CharSequenceUtil::isNotBlank)
            .collect(Collectors.toList());

        String[] originPatterns = allowedOrigins.isEmpty()
            ? new String[] {"*"}
            : allowedOrigins.toArray(String[]::new);

        registry.addMapping("/**")
            .allowedOriginPatterns(originPatterns)
            .allowCredentials(true)
            .allowedMethods("*")
            .allowedHeaders("*");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path examAssetRoot = storagePathSupport.ensureExamAssetRoot();
        String resourceLocation = examAssetRoot.toUri().toString();
        registry.addResourceHandler("/exam-assets/**")
            .addResourceLocations(resourceLocation.endsWith("/") ? resourceLocation : resourceLocation + "/");
    }
}
