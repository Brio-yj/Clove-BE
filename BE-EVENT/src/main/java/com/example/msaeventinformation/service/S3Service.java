package com.example.msaeventinformation.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.example.msaeventinformation.model.JsonFileInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final AmazonS3 amazonS3;

    @Value("${cloud.aws.s3.bucketName}")
    private String bucketName;

    // 브라우저에서 파일을 inline으로 디스플레이
    public String uploadFile(MultipartFile file) throws IOException {
        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(file.getContentType());
        metadata.setContentLength(file.getSize());

        metadata.setContentDisposition("inline");

        amazonS3.putObject(new PutObjectRequest(bucketName, fileName, file.getInputStream(), metadata));
        return amazonS3.getUrl(bucketName, fileName).toString();
    }

    public String uploadJsonFile(MultipartFile jsonFile) throws IOException {
        String fileName = UUID.randomUUID().toString() + "_" + jsonFile.getOriginalFilename();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("application/json");
        metadata.setContentLength(jsonFile.getSize());

        amazonS3.putObject(new PutObjectRequest(bucketName, fileName, jsonFile.getInputStream(), metadata));
        return amazonS3.getUrl(bucketName, fileName).toString();
    }

    public List<JsonFileInfo> getJsonFiles() {
        ObjectListing objectListing = amazonS3.listObjects(bucketName);

        return objectListing.getObjectSummaries().stream()
                .filter(s3Object -> s3Object.getKey().endsWith(".json"))
                .map(s3Object -> new JsonFileInfo(s3Object.getKey(), amazonS3.getUrl(bucketName, s3Object.getKey()).toString()))
                .collect(Collectors.toList());
    }

    public String getJsonFileUrlByFileName(String fileName) {
        if (amazonS3.doesObjectExist(bucketName, fileName)) {
            return amazonS3.getUrl(bucketName, fileName).toString();
        }
        throw new IllegalArgumentException("File not found: " + fileName);
    }
}
