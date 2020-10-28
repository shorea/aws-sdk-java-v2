/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.services.s3.internal.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static software.amazon.awssdk.services.s3.S3MockUtils.mockListObjectsResponse;

import java.net.URI;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.testutils.service.http.MockHttpClient;

/**
 * Functional tests for multi-region access point ARN
 */
public class MultiRegionAccessPointEndpointResolutionTest {

    private MockHttpClient mockHttpClient;

    @Before
    public void setup() {
        mockHttpClient = new MockHttpClient();
    }

    @Test
    public void multiRegionArn_correctlyRewritesEndpoint() throws Exception {
        URI customEndpoint = URI.create("https://myaccesspoint.123456789012.mrap.global-s3.amazonaws.com");
        mockHttpClient.stubNextResponse(mockListObjectsResponse());
        S3Client s3Client = clientBuilder().serviceConfiguration(S3Configuration.builder()
                                                                                .useArnRegionEnabled(true)
                                                                                .build())
                                           .build();
        String arn = "arn:aws:s3:global:123456789012:accesspoint:myaccesspoint";
        s3Client.listObjects(ListObjectsRequest.builder().bucket(arn).build());
        assertEndpointMatches(mockHttpClient.getLastRequest(), customEndpoint.toString());
    }

    @Test
    public void multiRegionArn_customEndpoint_throwsIllegalArgumentException() throws Exception {
        URI customEndpoint = URI.create("https://foobar.amazonaws.com");
        mockHttpClient.stubNextResponse(mockListObjectsResponse());
        S3Client s3Client = clientBuilder().endpointOverride(customEndpoint).build();
        String arn = "arn:aws:s3:global:123456789012:accesspoint:myaccesspoint";

        assertThatThrownBy(() -> s3Client.listObjects(ListObjectsRequest.builder().bucket(arn).build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("endpoint override");
    }

    @Test
    public void multiRegionArn_dualstackEnabled_throwsIllegalArgumentException() throws Exception {
        mockHttpClient.stubNextResponse(mockListObjectsResponse());
        S3Client s3Client = clientBuilder().serviceConfiguration(S3Configuration.builder()
                                                                                .dualstackEnabled(true)
                                                                                .useArnRegionEnabled(true)
                                                                                .build())
                                           .build();
        String arn = "arn:aws:s3:global:123456789012:accesspoint:myaccesspoint";

        assertThatThrownBy(() -> s3Client.listObjects(ListObjectsRequest.builder().bucket(arn).build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dualstack");
    }

    @Test
    public void multiRegionArn_fipsRegion_throwsIllegalArgumentException() throws Exception {
        mockHttpClient.stubNextResponse(mockListObjectsResponse());
        S3Client s3Client = clientBuilder().region(Region.of("fips-us-east-1"))
                                           .serviceConfiguration(S3Configuration.builder()
                                                                                .dualstackEnabled(false)
                                                                                .useArnRegionEnabled(true)
                                                                                .build())
                                           .build();
        String arn = "arn:aws:s3:global:123456789012:accesspoint:myaccesspoint";

        assertThatThrownBy(() -> s3Client.listObjects(ListObjectsRequest.builder().bucket(arn).build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("FIPS");
    }

    @Test
    public void multiRegionArn_accelerateEnabled_throwsIllegalArgumentException() throws Exception {
        mockHttpClient.stubNextResponse(mockListObjectsResponse());
        S3Client s3Client = clientBuilder().serviceConfiguration(S3Configuration.builder()
                                                                                .accelerateModeEnabled(true)
                                                                                .build())
                                           .build();
        String arn = "arn:aws:s3:global:123456789012:accesspoint:myaccesspoint";

        assertThatThrownBy(() -> s3Client.listObjects(ListObjectsRequest.builder().bucket(arn).build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("accelerate");
    }

    @Test
    public void multiRegionArn_pathStyle_throwsIllegalArgumentException() throws Exception {
        mockHttpClient.stubNextResponse(mockListObjectsResponse());
        S3Client s3Client = clientBuilder().serviceConfiguration(S3Configuration.builder()
                                                                                .pathStyleAccessEnabled(true)
                                                                                .build())
                                           .build();
        String arn = "arn:aws:s3:global:123456789012:accesspoint:myaccesspoint";

        assertThatThrownBy(() -> s3Client.listObjects(ListObjectsRequest.builder().bucket(arn).build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("path style addressing");
    }

    @Test
    public void multiRegionArn_differentRegion_useArnRegionTrue() throws Exception {
        URI customEndpoint = URI.create("https://myaccesspoint.123456789012.mrap.global-s3.amazonaws.com");
        mockHttpClient.stubNextResponse(mockListObjectsResponse());
        S3Client s3Client = clientBuilder().serviceConfiguration(b -> b.useArnRegionEnabled(true)).build();
        String arn = "arn:aws:s3:global:123456789012:accesspoint:myaccesspoint";

        s3Client.listObjects(ListObjectsRequest.builder().bucket(arn).build());

        assertEndpointMatches(mockHttpClient.getLastRequest(), customEndpoint.toString());
    }

    /**
     * Assert that the provided request would have gone to the given endpoint.
     *
     * @param capturedRequest Request captured by mock HTTP client.
     * @param endpoint        Expected endpoint.
     */
    private void assertEndpointMatches(SdkHttpRequest capturedRequest, String endpoint) {
        assertThat(capturedRequest.getUri()).isEqualTo(URI.create(endpoint));
    }

    /**
     * @return Client builder instance preconfigured with credentials and region using the {@link #mockHttpClient} for transport.
     */
    private S3ClientBuilder clientBuilder() {
        return S3Client.builder()
                       .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("akid", "skid")))
                       .region(Region.AP_SOUTH_1)
                       .httpClient(mockHttpClient);
    }
}