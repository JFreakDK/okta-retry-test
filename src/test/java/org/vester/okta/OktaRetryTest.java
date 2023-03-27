package org.vester.okta;

import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.okta.sdk.authc.credentials.TokenClientCredentials;
import com.okta.sdk.client.Clients;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openapitools.client.ApiClient;
import org.openapitools.client.api.UserApi;
import org.openapitools.client.model.User;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OktaRetryTest {

    @RegisterExtension
    static WireMockExtension wireMockExtension = WireMockExtension.newInstance()
            .options(wireMockConfig()
                    .dynamicPort()
                    .notifier(new ConsoleNotifier(true))
            )
            .build();

    final String TEST_API_TOKEN = "TestOktaApiToken";

    @Test
    @DisplayName("should retrieve user (by retrying) before global timeout")
    void testRetryMechanismAfterTimeout() {
        String secondState = "secondState";
        stubGetUser("Jane", 4000, Scenario.STARTED, secondState);
        stubGetUser("Joe", 1000, secondState, "unusedState");

        ApiClient client = Clients.builder()
                .setOrgUrl("http://localhost:" + wireMockExtension.getRuntimeInfo().getHttpPort())  // e.g. https://dev-123456.okta.com
                .setClientCredentials(new TokenClientCredentials(TEST_API_TOKEN))
                .setRetryMaxAttempts(4)
                .setRetryMaxElapsed(10)
                .setConnectionTimeout(2)
                .build();

        UserApi userApi = new UserApi(client);

        User user = userApi.getUser("username");

        assertEquals(user.getProfile().getFirstName(), "Joe");
    }

    private void stubGetUser(String name, int fixedDelayInMilliseconds, String currentState, String nextState) {
        wireMockExtension.stubFor(get("/api/v1/users/username")
                .withHeader("Authorization", equalTo("SSWS " + TEST_API_TOKEN))
                .inScenario("testRetry")
                .whenScenarioStateIs(currentState)
                .willSetStateTo(nextState)
                .willReturn(ok()
                        .withFixedDelay(fixedDelayInMilliseconds)
                        .withHeader("Content-type", "application/json")
                        .withBody("""
                                {
                                  "profile": {
                                    "firstName": "%s",
                                    "lastName": "sampleLastName",
                                    "mobilePhone": "+4580808080",
                                    "accountId": "98f0b29c-34eb-4862-b8ec-e900cf42bb86",
                                    "email": "93c70210-c5f1-4f48-9322-2cf1891ba674@example.com",
                                    "secondEmail": "sample@example.com"
                                  },
                                  "id": "testUser"
                                }
                                """.formatted(name)))

        );
    }
}