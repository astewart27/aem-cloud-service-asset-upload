package com.slalom.playground.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.slalom.playground.dto.AssetUploadDto;
import com.slalom.playground.service.AssetUploadService;

import lombok.RequiredArgsConstructor;

import org.apache.commons.lang3.StringUtils;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@RestController
@RequestMapping("/api/asset")
public class PlaygroundController {
    
    Logger LOGGER = LoggerFactory.getLogger(PlaygroundController.class);

    private AssetUploadService assetUploadService;

    public PlaygroundController(@Autowired AssetUploadService assetUploadService) {
        this.assetUploadService = assetUploadService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> assetUpload(@ModelAttribute AssetUploadDto uploadDto) {

        String folderPath = uploadDto.getFolderPath();
        List<MultipartFile> files = uploadDto.getFiles();

        LOGGER.info("In controller :: assetUpload");
        if (StringUtils.isEmpty(folderPath) || !folderPath.startsWith("/content/dam") || files.size() < 1) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        String response = assetUploadService.uploadAssetsToCloud(folderPath, files);
        LOGGER.info("In controller :: assetUpload :: response {}", response);

        return new ResponseEntity<>(HttpStatus.OK);
    }
    
}
