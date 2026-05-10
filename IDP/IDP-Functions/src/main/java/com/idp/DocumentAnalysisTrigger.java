package com.idp;

import com.azure.ai.documentintelligence.DocumentIntelligenceClient;
import com.azure.ai.documentintelligence.DocumentIntelligenceClientBuilder;
import com.azure.ai.documentintelligence.models.*;
import com.azure.core.util.BinaryData;
import com.azure.core.util.polling.SyncPoller;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.azure.core.http.policy.AddHeadersPolicy;
import com.azure.core.http.HttpHeaders;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Map;

public class DocumentAnalysisTrigger {

    @FunctionName("DocumentAnalysisTrigger")
    public void run(
        @BlobTrigger(name = "content", path = "idp-documents/{name}", connection = "AzureWebJobsStorage") byte[] content,
        @com.microsoft.azure.functions.annotation.BindingName("name") String name,
        ExecutionContext context
    ) {
        context.getLogger().info("Processing document: " + name);

        // 1. Get Environment Settings
        String endpoint = System.getenv("AZURE_DOCUMENT_INTELLIGENCE_ENDPOINT");
        String key = System.getenv("AZURE_DOCUMENT_INTELLIGENCE_KEY");
        String dbConnectionString = System.getenv("DB_CONNECTION_STRING");

        // 2. Initialize AI Client with Manual Header Policy (Fixes Version Conflicts)
        HttpHeaders headers = new HttpHeaders().set("Ocp-Apim-Subscription-Key", key);
        AddHeadersPolicy authPolicy = new AddHeadersPolicy(headers);

        DocumentIntelligenceClient client = new DocumentIntelligenceClientBuilder()
            .endpoint(endpoint)
            .addPolicy(authPolicy)
            .buildClient();

        try {
            // 3. Analyze the document using the Prebuilt Invoice model
            AnalyzeDocumentOptions options = new AnalyzeDocumentOptions(BinaryData.fromBytes(content));
            SyncPoller<AnalyzeOperationDetails, AnalyzeResult> poller = 
                client.beginAnalyzeDocument("prebuilt-invoice", options);

            AnalyzeResult result = poller.getFinalResult();

            // 4. Process Extracted Documents
            if (result.getDocuments() != null && !result.getDocuments().isEmpty()) {
                
                // 5. Establish Database Connection with Retry Logic (Fixes DB Timeout/Pause)
                Connection conn = null;
                int retryCount = 0;
                int maxRetries = 3;

                while (retryCount < maxRetries) {
                    try {
                        context.getLogger().info("Connecting to Database (Attempt " + (retryCount + 1) + ")...");
                        conn = DriverManager.getConnection(dbConnectionString);
                        break; // Connection successful, exit loop
                    } catch (Exception dbEx) {
                        retryCount++;
                        if (retryCount >= maxRetries) {
                            context.getLogger().severe("Could not connect to DB after " + maxRetries + " attempts.");
                            throw dbEx;
                        }
                        context.getLogger().warning("Database is waking up or busy. Retrying in 15 seconds...");
                        Thread.sleep(15000); // Wait for serverless DB to resume
                    }
                }

                try {
                    String sql = "INSERT INTO dbo.ValidatedDocuments (VendorName, TotalAmount, InvoiceDate, ProcessedTimestamp) VALUES (?, ?, ?, ?)";
                    
                    for (AnalyzedDocument document : result.getDocuments()) {
                        Map<String, DocumentField> fields = document.getFields();

                        // Map AI fields to variables
                        String vendorName = fields.containsKey("VendorName") ? fields.get("VendorName").getContent() : "Unknown Vendor";
                        
                        Double totalAmount = null;
                        if (fields.containsKey("InvoiceTotal") && fields.get("InvoiceTotal").getValueCurrency() != null) {
                            totalAmount = fields.get("InvoiceTotal").getValueCurrency().getAmount();
                        }

                        String invoiceDate = fields.containsKey("InvoiceDate") ? fields.get("InvoiceDate").getContent() : null;
                        
                        // System Timestamp for the database record
                        Timestamp processedTimestamp = new Timestamp(System.currentTimeMillis());

                        // 6. Execute SQL Insert
                        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                            stmt.setString(1, vendorName);
                            if (totalAmount != null) {
                                stmt.setDouble(2, totalAmount);
                            } else {
                                stmt.setNull(2, java.sql.Types.DECIMAL);
                            }
                            stmt.setString(3, invoiceDate);
                            stmt.setTimestamp(4, processedTimestamp);

                            stmt.executeUpdate();
                            context.getLogger().info("Successfully saved record for: " + vendorName);
                        }
                    }
                } finally {
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                    }
                }
            } else {
                context.getLogger().warning("No documents detected in file: " + name);
            }

        } catch (Exception e) {
            context.getLogger().severe("Critical Failure: " + e.getMessage());
        }
    }
}