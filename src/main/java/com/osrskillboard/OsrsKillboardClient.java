package com.osrskillboard;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import okhttp3.*;

import javax.swing.*;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class OsrsKillboardClient
{
    private final OkHttpClient httpClient;
    private static final HttpUrl apiBase = HttpUrl.parse("https://api.osrskillboard.com/");

    @Inject
    private ChatMessageManager chatMessageManager;

    @Inject
    private OsrsKillboardClient(OkHttpClient client, Gson gson)
    {
        this.httpClient = client;
    }


    public CompletableFuture<Void> submit(Client client, JsonObject killRecord, OsrsKillboardPanel panel, String victimName, int victimCombat, OsrsKillboardItem[] victimLoot)
    {
        CompletableFuture<Void> future = new CompletableFuture<>();

        HttpUrl url = apiBase.newBuilder()
                .addPathSegment("pks")
                .build();

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");

        Request requestBuilder = new Request.Builder()
                .post(RequestBody.create(JSON, killRecord.toString()))
                .url(url)
                .build();

        httpClient.newCall(requestBuilder).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).value("OSRSKillboard.com - Kill of " + victimName + " failed to log.").build());
                SwingUtilities.invokeLater(() -> panel.add(victimName, victimCombat, victimLoot, ""));
                log.warn("unable to submit pk", e);
            }

            @Override
            public void onResponse(Call call, Response response) {

                String killIdentifier = null;
                try {
                    assert response.body() != null;
                    killIdentifier = response.body().string();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    response.close();
                }

                chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).value("OSRSKillboard.com - Kill of " + victimName + " logged.").build());

                String finalKillIdentifier = killIdentifier;
                SwingUtilities.invokeLater(() -> {
                    panel.add(victimName, victimCombat, victimLoot, finalKillIdentifier);
                    future.complete(null);
                });
            }
        });

        return future;
    }
}