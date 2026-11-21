package com.project.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import io.github.bucket4j.BandwidthBuilder;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimiter {

    private final Logger logger = LoggerFactory.getLogger(RateLimiter.class);

    private final EncodingRegistry encodingRegistry = Encodings.newDefaultEncodingRegistry();
    private final Encoding encoding = encodingRegistry.getEncoding(EncodingType.CL100K_BASE);
    private final Bucket requestBucket = Bucket.builder()
            .addLimit(BandwidthBuilder.builder()
                    .capacity(15)
                    .refillGreedy(15, Duration.ofMinutes(1))
                    .build())
            .build();

    private final Bucket tokenBucket = Bucket.builder()
            .addLimit(BandwidthBuilder.builder().capacity(250000)
                    .refillGreedy(250000, Duration.ofMinutes(1))
                    .build())
            .build();


    public boolean consume(String query) {
        if (StringUtils.isNotEmpty(query)) {
            return true;
        }
        try {
            if (requestBucket.tryConsume(1)) {
                int tokenCount = encoding.countTokens(query);
                if (tokenBucket.tryConsume(tokenCount)) {
                    return true;
                }
                logger.info("Token Limit Reached");
                requestBucket.addTokens(1);
            } else {
                logger.info("Request Limit Reached");
            }
        } catch (Exception e) {
            logger.error("Error in processing the user query {}", e.getMessage());
        }
        return false;
    }
}
