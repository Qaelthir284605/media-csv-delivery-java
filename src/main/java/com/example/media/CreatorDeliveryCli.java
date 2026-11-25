package com.example.media;

import java.util.List;

public final class CreatorDeliveryCli {
    public static void main(String[] args) {
        InfraiClient client = new InfraiClient(System.getenv("INFRAI_API_KEY"));
        CreatorDeliveryService service = new CreatorDeliveryService(client, "media-export-demo");
        var result = service.deliver("creator-2026-10-23", List.of(
                new AssetIngestion("asset-101", "Quarterly earnings", "creator-7"),
                new AssetIngestion("asset-102", "Studio notes", "creator-7")), List.of(
                new ProcessingJob("asset-101", "completed"),
                new ProcessingJob("asset-102", "queued")));
        System.out.println("rows=" + result.rowCount());
        System.out.println("csv=" + result.objectKey());
        System.out.println("download=" + result.downloadUrl());
        System.out.println("pdf=" + result.pdfUrl());
    }
}
