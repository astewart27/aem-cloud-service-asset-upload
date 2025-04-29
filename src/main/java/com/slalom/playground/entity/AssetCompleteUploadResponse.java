package com.slalom.playground.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatusCode;

@Getter
@Setter
public class AssetCompleteUploadResponse {

    private String fileName;
    private HttpStatusCode status;
    private String message;

    public AssetCompleteUploadResponse() {}

    public AssetCompleteUploadResponse(String fileName, HttpStatusCode status, String message) {
        this.fileName = fileName;
        this.status = status;
        this.message = message;
    }
}
