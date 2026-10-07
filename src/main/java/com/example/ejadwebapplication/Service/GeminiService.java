package com.example.ejadwebapplication.Service;

import com.example.ejadwebapplication.Api.ApiException;
import com.google.genai.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    // نفس الموديل اللي يستخدمه AiService
    @Value("${ai.model}")
    private String model;

    public String generateText(String prompt) {
        try {
            Client client = Client.builder().apiKey(apiKey).build();
            return client.models.generateContent(model, prompt, null).text();
        } catch (Exception e) {
            throw new ApiException("Could not generate the admin report, please try again later");
        }
    }
}
