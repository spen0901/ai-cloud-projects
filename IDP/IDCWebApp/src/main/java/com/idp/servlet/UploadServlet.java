package com.idp.servlet;

import com.azure.storage.blob.*;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@WebServlet("/UploadServlet")
@MultipartConfig // Required to handle file uploads
public class UploadServlet extends HttpServlet {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final String CONNECTION_STRING = "REPLACED_BY_ENV_VAR";";
	//System.getenv("AZURE_STORAGE_CONNECTION_STRING");
    private static final String CONTAINER_NAME = "idp-documents";//"raw-uploads";

    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // 1. Get the file part from the request
        Part filePart = request.getPart("documentFile"); 
        String fileName = filePart.getSubmittedFileName();
        
        // 2. Generate a unique name to prevent overwriting in Azure
        String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName;
        
        System.out.println("Connection String is:"+CONNECTION_STRING);
        
        System.out.println("Connecting to Blob Storage");

        // 3. Connect to Azure Blob Storage
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(CONNECTION_STRING).buildClient();
        System.out.println("Connected to Blob Storage");
        System.out.println("Connecting to Blob Container");
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(CONTAINER_NAME);
        System.out.println("Connected to Blob Container");
        
        // 4. Upload the file
        try (InputStream is = filePart.getInputStream()) {
            BlobClient blobClient = containerClient.getBlobClient(uniqueFileName);
            blobClient.upload(is, filePart.getSize(), true);
            
            // 5. Success: Redirect back to the form or a success page
            response.getWriter().println("File uploaded successfully to Azure: " + uniqueFileName);
        } catch (Exception e) {
            response.getWriter().println("Error: " + e.getMessage());
        }
        
    }
}