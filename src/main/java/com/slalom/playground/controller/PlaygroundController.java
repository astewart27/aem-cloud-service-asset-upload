package com.slalom.playground.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slalom.playground.constants.Constants;
import com.slalom.playground.entity.AssetCompleteUploadResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.slalom.playground.dto.AssetUploadDto;
import com.slalom.playground.service.AssetUploadService;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/asset")
public class PlaygroundController {
    
    Logger LOGGER = LoggerFactory.getLogger(PlaygroundController.class);

    private AssetUploadService assetUploadService;

    public PlaygroundController(@Autowired AssetUploadService assetUploadService) {
        this.assetUploadService = assetUploadService;
    }

    // Additionally can create a FE application to handle file upload request to /upload endpoint.
    @GetMapping("/")
    public String home() {
        return "index";
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> assetUpload(@ModelAttribute AssetUploadDto uploadDto) {

        String folderPath = uploadDto.getFolderPath();
        List<MultipartFile> files = uploadDto.getFiles();

        if (StringUtils.isEmpty(folderPath) || !folderPath.startsWith(Constants.CONTENT_PATH) || files.isEmpty()) {
            return new ResponseEntity<>(Constants.INVALID_PARAMETERS_ERROR, HttpStatus.BAD_REQUEST);
        }

        String response = assetUploadService.uploadAssetsToCloud(folderPath, files);

        JsonArray responseArray = JsonParser.parseString(response).getAsJsonArray();
        for (JsonElement element : responseArray) {
            JsonObject file = element.getAsJsonObject();
            String fileName = file.get(Constants.FILE_NAME).getAsString();
            String message = file.get(Constants.MESSAGE).getAsString();
            if (message.equals(Constants.SUCCESSFUL)) {
                LOGGER.info("{} uploaded asset {} to {}", message, fileName, folderPath.concat(Constants.SLASH).concat(fileName));
            } else {
                LOGGER.info("{} to upload asset {} to {}", message, fileName, folderPath.concat(Constants.SLASH).concat(fileName));
            }
        }

        return new ResponseEntity<>(!responseArray.isEmpty() ? HttpStatus.OK : HttpStatus.BAD_REQUEST);
    }
    
}
