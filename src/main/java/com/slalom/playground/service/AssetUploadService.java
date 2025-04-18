package com.slalom.playground.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface AssetUploadService {
    
    String uploadAssetsToCloud(String folderPath, List<MultipartFile> files);
}
