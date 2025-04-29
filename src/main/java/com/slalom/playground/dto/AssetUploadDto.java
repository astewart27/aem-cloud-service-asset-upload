package com.slalom.playground.dto;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import lombok.Data;

@Data
public class AssetUploadDto {
    
    private String folderPath;
    private List<MultipartFile> files;
    
}
