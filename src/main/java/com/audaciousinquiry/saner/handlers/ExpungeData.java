package com.audaciousinquiry.saner.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.audaciousinquiry.saner.Utility;
import com.audaciousinquiry.saner.config.Oauth2Config;
import com.audaciousinquiry.saner.exceptions.SanerLambdaException;
import com.audaciousinquiry.saner.model.Job;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ExpungeData implements RequestHandler<Void, String> {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(ExpungeData.class);

    @Override
    public String handleRequest(Void unused, Context context) {
        String returnValue;

        log.info("ExpungeData Lambda - Started");

        try {
            String secretName = System.getenv("API_AUTH_SECRET");
            Region region = Region.of(System.getenv("AWS_REGION"));
            String expungeApiUrl = System.getenv("API_ENDPOINT");

            Oauth2Config oauth2Config = Oauth2Config.fromAwsSecret(region, secretName);
            log.info("Oauth2 Config Obtained From AWS Secret");

            AccessToken accessToken = Utility.getOauth2AccessToken(oauth2Config);
            log.info("Access Token Obtained");

            HttpResponse<String> response;
            try (HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()) {

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(expungeApiUrl))
                        .header("Authorization", accessToken.toAuthorizationHeader())
                        .DELETE()
                        .build();

                response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());
            }

            Job job = objectMapper.readValue(response.body(), Job.class);
            returnValue = job.getId();

            log.info("API Call Status: {}, Saner Job ID: {}",
                    response.statusCode(),
                    job.getId()
            );

            log.info("ExpungeData Lambda - Completed");
        } catch (URISyntaxException | IOException | ParseException ex) {
            throw new SanerLambdaException(ex.getMessage());
        }   catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SanerLambdaException(ex.getMessage());
        }

        return returnValue;
    }
}
