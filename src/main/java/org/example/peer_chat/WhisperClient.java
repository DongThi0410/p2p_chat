package org.example.peer_chat;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class WhisperClient {
    private final String serverUrl;
    private final HttpClient client;

    public WhisperClient(String serverUrl) {
        this.serverUrl = serverUrl;
        this.client = HttpClient.newHttpClient();

    }
    public String transcribe(File audioFile) throws IOException, InterruptedException {
        String boundary = "------------------------" + UUID.randomUUID().toString().replace("-", "");

        byte[] fileBytes = readAllBytes(audioFile);

        String fileHeader = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"" + audioFile.getName() + "\"\r\n" +
                "Content-Type: audio/webm\r\n\r\n";

        String fileFooter = "\r\n--" + boundary + "--\r\n";

        // Build full request body
        byte[] headerBytes = fileHeader.getBytes(StandardCharsets.UTF_8);
        byte[] footerBytes = fileFooter.getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[headerBytes.length + fileBytes.length + footerBytes.length];

        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, body, headerBytes.length, fileBytes.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + fileBytes.length, footerBytes.length);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/transcribe"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            // Trả về text transcription
            return response.body();
        } else {
            throw new IOException("HTTP error: " + response.statusCode() + " - " + response.body());
        }
    }

    private byte[] readAllBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return fis.readAllBytes();
        }
    }

    public static void main(String[] args) throws Exception {
        WhisperClient client = new WhisperClient("http://localhost:8000");
        File audioFile = new File("test.webm");
        String result = client.transcribe(audioFile);
        System.out.println("Transcription: " + result);
    }
}