package com.slalom.playground.entity;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class AssetBinaryUploadResponse {

    private String fileName;
    private HttpStatusCode status;
    private String message;
    private int partIndex;

    public AssetBinaryUploadResponse(String fileName, HttpStatusCode status, String message, int partIndex) {
        this.fileName = fileName;
        this.status = status;
        this.message = message;
        this.partIndex = partIndex;
    }
}
