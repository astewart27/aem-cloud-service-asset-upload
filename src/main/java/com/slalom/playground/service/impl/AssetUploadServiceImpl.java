package com.slalom.playground.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.slalom.playground.constants.Constants;
import com.slalom.playground.entity.AssetCompleteUploadResponse;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.slalom.playground.config.AemConfig;
import com.slalom.playground.config.FileType;
import com.slalom.playground.service.AssetUploadService;
import com.slalom.playground.entity.AssetBinaryUploadResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Service
public class AssetUploadServiceImpl implements AssetUploadService {

    Logger LOGGER = LoggerFactory.getLogger(AssetUploadServiceImpl.class);

    @Autowired
    private AemConfig aemConfig;

    RestTemplate restTemplate;

    public AssetUploadServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String uploadAssetsToCloud(String folderPath, List<MultipartFile> files) {
        LOGGER.info("In uploadAssetsToCloud :: folderPath {} - files {}", folderPath, files);

        String authorUrl = aemConfig.getAuthorUrl();

        // Step 1: Initiate Upload
        ResponseEntity<String> initiateUploadResponse = initiateUploadRequest(authorUrl, folderPath, files);

        // Step 2: Upload Asset Binaries
        List<AssetBinaryUploadResponse> filtered = new ArrayList<>();
        if (initiateUploadResponse.getStatusCode().is2xxSuccessful() && initiateUploadResponse.getBody() != null) {
            String initiateUploadResponseBody = initiateUploadResponse.getBody();
            JsonObject uploadResponse = JsonParser.parseString(initiateUploadResponseBody).getAsJsonObject();
            try {
                List<AssetBinaryUploadResponse> binaryResponses = uploadBinaryRequest(uploadResponse, files);
                filtered = binaryResponses.stream().filter(x -> x.getStatus().is2xxSuccessful()).toList();
            } catch (IOException exception) {
                LOGGER.info("Error uploading asset binaries: {}", exception.getMessage());
            }
        }

        // Step 3: Complete Upload
        String completedUpload = null;
        if (!filtered.isEmpty()) {
            List<AssetCompleteUploadResponse> completeUploadResponse = completeUploadRequest(authorUrl, filtered, initiateUploadResponse);
            try {
                ObjectMapper mapper = new ObjectMapper();
                completedUpload = mapper.writeValueAsString(completeUploadResponse);
            } catch (JsonProcessingException e) {
                LOGGER.info("In uploadAssetsToCloud :: JsonProcessingException error {}", e.getMessage());
                throw new RuntimeException(e);
            }

        }
        return completedUpload;
    }

    // Step 1: Initiate Upload to AEMaaCS
    private ResponseEntity<String> initiateUploadRequest(String authorUrl, String folderPath, List<MultipartFile> files) {
        LOGGER.info("In initiateUploadRequest");
        String initiateUploadUrl = authorUrl.concat(folderPath).concat(Constants.INITIATE_UPLOAD_SELECTOR);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBearerAuth("someAccessToken");

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        for (MultipartFile file: files) {
            String fileName = file.getOriginalFilename();
            if (fileName != null) {
                String fileExtension = FilenameUtils.getExtension(fileName);
                boolean isValidExtension = checkFileExtension(fileExtension);
                if (isValidExtension) {
                    formData.add(Constants.FILE_NAME, file.getOriginalFilename());
                    formData.add(Constants.FILE_SIZE, String.valueOf(file.getSize()));
                }
            }
        }

        if (!formData.isEmpty()) {
            try {
                HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
                return this.restTemplate.exchange(initiateUploadUrl, HttpMethod.POST, request, String.class);
            } catch (HttpClientErrorException httpRestClientException) {
                LOGGER.info("Error in initiateUploadRequest request {}", httpRestClientException.getMessage());
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
        }
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    // Step 2: Upload binaries to Binary Cloud Blob Storage
    private List<AssetBinaryUploadResponse> uploadBinaryRequest(JsonObject initiateUploadResponse, List<MultipartFile> files) throws IOException {
        LOGGER.info("In uploadBinaryRequest");
        List<AssetBinaryUploadResponse> responses = new ArrayList<>();

        JsonArray initUploadResFiles = initiateUploadResponse.getAsJsonArray(Constants.FILES);
        for (MultipartFile file : files) {
            for (JsonElement initFile: initUploadResFiles) {
                JsonObject currentFile = initFile.getAsJsonObject();
                if (StringUtils.equals(file.getOriginalFilename(), currentFile.get(Constants.FILE_NAME).getAsString())) {
                    // If file size < maxPartSize - upload all bytes to first uploadURI - ignore the rest of uploadURIs
                    if (file.getSize() < currentFile.get(Constants.MAX_PART_SIZE).getAsInt()) {
                        byte[] fileBytes = IOUtils.toByteArray(file.getInputStream());
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.parseMediaType(currentFile.get(Constants.MIME_TYPE).getAsString()));
                        headers.setContentLength(fileBytes.length);
                        headers.set(Constants.ACCEPT_HEADER, Constants.SUPPORTED_MIME_TYPES);
                        HttpEntity<byte[]> request = new HttpEntity<>(fileBytes, headers);
                        URI uri = URI.create(currentFile.getAsJsonArray(Constants.UPLOAD_URIS).get(0).getAsString());
                        this.restTemplate.getInterceptors().clear();
                        try {
                            ResponseEntity<String> responseEntity = this.restTemplate.exchange(uri, HttpMethod.PUT, request, String.class);
                            boolean uploadSuccessful =  responseEntity.getStatusCode().is2xxSuccessful();
                            responses.add(new AssetBinaryUploadResponse(file.getOriginalFilename(), responseEntity.getStatusCode(), uploadSuccessful ? Constants.SUCCESSFUL : Constants.FAILED, 1));
                        } catch (HttpClientErrorException httpRestClientException) {
                            LOGGER.info("Error uploading asset binaries {}", httpRestClientException.getMessage());
                        }
                    } else {
                        // Large asset: file size > maxPartSize - Upload in chunks
                        long fileSize = file.getBytes().length;
                        int minPartSize = currentFile.get(Constants.MIN_PART_SIZE).getAsInt();
                        int maxPartSize = currentFile.get(Constants.MAX_PART_SIZE).getAsInt();
                        int totalParts = calculatePartCount(fileSize, maxPartSize);
                        String mimeType = currentFile.get(Constants.MIME_TYPE).getAsString();
                        String fileName = currentFile.get(Constants.FILE_NAME).getAsString();
                        JsonArray uploadURIs = currentFile.getAsJsonArray(Constants.UPLOAD_URIS);
                        long[] partSizes = calculatePartSizes(fileSize, totalParts, minPartSize, maxPartSize);
                        try (InputStream inputStream = file.getInputStream()) {
                            List<ResponseEntity<String>> entities = new ArrayList<>();
                            for (int partIndex = 0; partIndex < partSizes.length; partIndex++) {
                                long partSize = partSizes[partIndex];
                                byte[] chunkData = inputStream.readNBytes((int) partSize);
                                String uploadUrl = uploadURIs.get(partIndex).getAsString();
                                entities.add(uploadChunk(uploadUrl, chunkData, mimeType));
                            }
                            if (entities.stream().allMatch(x -> x.getStatusCode().is2xxSuccessful())) {
                                AssetBinaryUploadResponse assetBinaryUploadResponse = new AssetBinaryUploadResponse(fileName, entities.get(0).getStatusCode(), entities.get(0).getStatusCode().is2xxSuccessful() ? Constants.SUCCESSFUL : Constants.FAILED, 1);
                                responses.add(assetBinaryUploadResponse);
                            }
                        }
                    }
                }
            }
        }
        LOGGER.info("Exiting uploadBinaryRequest");
        return responses;
    }

    // Step 3: Complete Upload to AEMaaCS
    private List<AssetCompleteUploadResponse> completeUploadRequest(String authorUrl, List<AssetBinaryUploadResponse> filtered, ResponseEntity<String> initiateUploadResponse) {
        LOGGER.info("In completeUploadRequest");
        List<AssetCompleteUploadResponse> responses = new ArrayList<>();
        String initiateUploadResponseBody = initiateUploadResponse.getBody();
        if (initiateUploadResponseBody != null) {
            JsonObject initiateUploadResponseObj = JsonParser.parseString(initiateUploadResponseBody).getAsJsonObject();
            try {
                String completeUrl = authorUrl.concat(initiateUploadResponseObj.get(Constants.COMPLETE_URI).getAsString());
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                headers.setBearerAuth("someAccessToken");

                JsonArray filesArray = initiateUploadResponseObj.getAsJsonArray(Constants.FILES);
                for (AssetBinaryUploadResponse assetBinaryUploadResponse : filtered) {
                    String currentFile = assetBinaryUploadResponse.getFileName();
                    for (JsonElement file: filesArray) {
                        JsonObject fileObject = file.getAsJsonObject();
                        String fileName = fileObject.get(Constants.FILE_NAME).getAsString();
                        String mimeType = fileObject.get(Constants.MIME_TYPE).getAsString();
                        String uploadToken = fileObject.get(Constants.UPLOAD_TOKEN).getAsString();
                        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
                        if (currentFile.equals(fileName)) {
                            formData.add(Constants.MIME_TYPE, mimeType);
                            formData.add(Constants.FILE_NAME, fileName);
                            formData.add(Constants.UPLOAD_TOKEN, uploadToken);
                            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
                            ResponseEntity<String> responseEntity = this.restTemplate.exchange(completeUrl, HttpMethod.POST, request, String.class);
                            responses.add(new AssetCompleteUploadResponse(fileName, responseEntity.getStatusCode(), responseEntity.getStatusCode().is2xxSuccessful() ? Constants.SUCCESSFUL : Constants.FAILED));
                        }
                    }
                }
            } catch (HttpClientErrorException httpRestClientException) {
                LOGGER.info("Error during complete upload {}", httpRestClientException.getMessage());
            }
        }
        LOGGER.info("Exiting completeUploadRequest");
        return responses;
    }

    // Helper method to upload large assets in chunks
    private ResponseEntity<String> uploadChunk(String uploadUrl, byte[] chunkData, String mimeType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mimeType));
        headers.setContentLength(chunkData.length);
        headers.set(Constants.ACCEPT_HEADER, Constants.SUPPORTED_MIME_TYPES);
        HttpEntity<byte[]> request = new HttpEntity<>(chunkData, headers);
        URI uri = URI.create(uploadUrl);
        this.restTemplate.getInterceptors().clear();
        try {
            return this.restTemplate.exchange(uri, HttpMethod.PUT, request, String.class);
        } catch (HttpClientErrorException httpRestClientException) {
            LOGGER.info("Error during upload asset binary chunk {}", httpRestClientException.getMessage());
        }
        return null;
    }

    // Helper method: Calculate part sizes
    private long[] calculatePartSizes(long totalSize, int partCount, int minPartSize, int maxPartSize) {
        long[] sizes = new long[partCount];
        long remaining = totalSize;
        for (int i = 0; i < partCount; i++) {
            if (i == partCount - 1) {
                sizes[i] = remaining;
            } else {
                sizes[i] = Math.min(maxPartSize, remaining - ((long) (partCount - 1 - i) * minPartSize));
            }
            remaining -= sizes[i];
        }
        return sizes;
    }

    // Helper method: Calculate part count
    private int calculatePartCount(long totalSize, int maxPartSize) {
        int parts = (int) (totalSize / maxPartSize);
        long remainder = totalSize % maxPartSize;
        if (remainder > 0) {
            parts ++;
        }
        return parts;
    }

    // Helper method: Check file extension to see if it's a supported file type
    private boolean checkFileExtension(String fileExtension) {
        for (FileType extension : FileType.values()) {
            if (extension.name().equalsIgnoreCase(fileExtension)) {
                return true;
            }
        }
        return false;
    }

    // Helper method: Additional Check before kicking off Asset Upload process
    // Note: In first step, initiate upload, folderPath must exist
    // if not, response will be 400/404 - check and implement checkIfFolderPathExists method if needed
    private boolean checkIfFolderPathExists(String authorUrl, String folderPath) {
        String folderUrl = authorUrl.concat(folderPath);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("someAccessToken");
        try {
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(headers);
            ResponseEntity<String> responseEntity = this.restTemplate.exchange(folderUrl.concat(Constants.JSON_EXTENSION), HttpMethod.GET, request, String.class);
            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                return true;
            } else {
                String folder = folderPath.substring(folderPath.lastIndexOf(Constants.SLASH) + 1);
                MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                formData.add(Constants.NAME, folder);
                formData.add(Constants.TITLE, folder);
                HttpEntity<MultiValueMap<String, String>> createFolderRequest = new HttpEntity<>(formData, headers);
                ResponseEntity<String> createFolderEntity = this.restTemplate.exchange(folderUrl, HttpMethod.POST, createFolderRequest, String.class);
                return createFolderEntity.getStatusCode().is2xxSuccessful();
            }

        } catch (HttpClientErrorException httpRestClientException) {
            LOGGER.info("Error in initiateUploadRequest request {}", httpRestClientException.getMessage());
            return false;
        }
    }
}
