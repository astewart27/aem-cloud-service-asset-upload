package com.slalom.playground.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatusCode;

@Getter
@Setter
public class AssetBinaryUploadResponse {

    private String fileName;
    private HttpStatusCode status;
    private String message;
    private int partIndex;

    public AssetBinaryUploadResponse() {}

    public AssetBinaryUploadResponse(String fileName, HttpStatusCode status, String message, int partIndex) {
        this.fileName = fileName;
        this.status = status;
        this.message = message;
        this.partIndex = partIndex;
    }
}
