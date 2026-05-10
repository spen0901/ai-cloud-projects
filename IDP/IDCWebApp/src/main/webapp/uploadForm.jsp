<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <title>IDP - Document Ingestion</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 50px; }
        .upload-container { border: 1px solid #ccc; padding: 20px; border-radius: 8px; width: 400px; }
        .btn { background-color: #0078d4; color: white; border: none; padding: 10px 20px; cursor: pointer; }
    </style>
</head>
<body>
    <h2>Upload Document for AI Processing</h2>
    <div class="upload-container">
        <form action="UploadServlet" method="post" enctype="multipart/form-data">
            
            <label for="docType">Document Type:</label><br>
            <input type="text" id="docType" name="documentType" placeholder="e.g. Invoice, Receipt"><br><br>
            
            <label for="file">Select File:</label><br>
            <input type="file" id="file" name="documentFile" required><br><br>
            
            <input type="submit" class="btn" value="Upload Document">
        </form>
    </div>
</body>
</html>