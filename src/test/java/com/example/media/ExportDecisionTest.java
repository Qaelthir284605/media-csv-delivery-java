package com.example.media;

import java.util.List;

public final class ExportDecisionTest {
    public static void main(String[] args) {
        var assets = List.of(new AssetIngestion("a-1", "Ledger talk", "c-1"), new AssetIngestion("a-2", "Pending cut", "c-1"));
        var jobs = List.of(new ProcessingJob("a-1", "completed"), new ProcessingJob("a-2", "queued"));
        var rows = CreatorDeliveryService.completedRows(assets, jobs);
        if (rows.size() != 1 || !rows.get(0).assetId().equals("a-1")) throw new AssertionError("only completed assets belong in the export");
        System.out.println("PASS completed-job delivery decision");
    }
}
