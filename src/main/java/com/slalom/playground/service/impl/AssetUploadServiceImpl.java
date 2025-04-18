package com.slalom.playground.service.impl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.slalom.playground.service.AssetUploadService;

@Service
public class AssetUploadServiceImpl implements AssetUploadService {

    Logger LOGGER = LoggerFactory.getLogger(AssetUploadServiceImpl.class);

    @Override
    public String uploadAssetsToCloud(String folderPath, List<MultipartFile> files) {
        LOGGER.info("In uploadAssetsToCloud :: folderPath {} - files {}", folderPath, files);
        return "";
    }
    
}
