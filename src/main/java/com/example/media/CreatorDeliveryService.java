package com.example.media;

import java.util.ArrayList;
import java.util.List;

public final class CreatorDeliveryService {
    private final InfraiClient infraiClient;
    private final String bucket;

    public CreatorDeliveryService(InfraiClient infraiClient, String bucket) {
        this.infraiClient = infraiClient;
        this.bucket = bucket;
    }

    public DeliveryResult deliver(String exportId, List<AssetIngestion> assets, List<ProcessingJob> jobs) {
        List<ExportRow> rows = completedRows(assets, jobs);
        if (rows.isEmpty()) throw new InfraiException("no completed assets are ready for delivery");
        String requestId = "export-" + exportId;
        infraiClient.createBucket(bucket, requestId + "-bucket");
        String csv = csv(rows);
        String key = "exports/" + exportId + ".csv";
        infraiClient.uploadObject(bucket, key, csv, requestId + "-upload");
        String downloadUrl = infraiClient.presignDownload(bucket, key, requestId + "-download");
        String pdfUrl = infraiClient.generatePdf(markdown(rows), requestId + "-document");
        return new DeliveryResult(key, csv, downloadUrl, pdfUrl, rows.size());
    }

    public static List<ExportRow> completedRows(List<AssetIngestion> assets, List<ProcessingJob> jobs) {
        List<ExportRow> result = new ArrayList<>();
        for (AssetIngestion asset : assets) for (ProcessingJob job : jobs)
            if (asset.assetId().equals(job.assetId()) && "completed".equals(job.state()))
                result.add(new ExportRow(asset.assetId(), asset.title(), asset.creatorId(), job.state()));
        return result;
    }

    private static String csv(List<ExportRow> rows) {
        StringBuilder out = new StringBuilder("asset_id,title,creator_id,job_state\n");
        for (ExportRow row : rows) out.append(row.assetId()).append(',').append(row.title()).append(',').append(row.creatorId()).append(',').append(row.jobState()).append('\n');
        return out.toString();
    }

    private static String markdown(List<ExportRow> rows) {
        StringBuilder out = new StringBuilder("# Creator export\n\n| Asset | Title | Creator | State |\n|---|---|---|---|\n");
        for (ExportRow row : rows) out.append('|').append(row.assetId()).append('|').append(row.title()).append('|').append(row.creatorId()).append('|').append(row.jobState()).append("|\n");
        return out.toString();
    }

    public record DeliveryResult(String objectKey, String csv, String downloadUrl, String pdfUrl, int rowCount) {}
}
