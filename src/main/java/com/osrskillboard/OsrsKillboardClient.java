package com.osrskillboard;

import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.http.api.RuneLiteAPI;
import okhttp3.*;

import javax.swing.*;
import java.io.IOException;

@Slf4j
@AllArgsConstructor
public class OsrsKillboardClient
{
    public static String baseUrl = "https://api.osrskillboard.com/";

    public void submit(Client client, JsonObject killRecord, OsrsKillboardPanel panel, String victimName, int victimCombat, OsrsKillboardItem[] victimLoot)
    {
        HttpUrl url = HttpUrl.parse(baseUrl + "pks").newBuilder().build();
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");

        Request request = new Request.Builder()
                .post(RequestBody.create(JSON, killRecord.toString()))
                .url(url)
                .build();

        RuneLiteAPI.CLIENT.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "OSRSKillboard.com - Kill of " + victimName + " failed to log." , null);
                SwingUtilities.invokeLater(() -> panel.add(victimName, victimCombat, victimLoot, ""));
                log.warn("unable to submit pk", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "OSRSKillboard.com - Kill of " + victimName + " logged.", null);

                String killIdentifier = response.body().string();
                SwingUtilities.invokeLater(() -> panel.add(victimName, victimCombat, victimLoot, killIdentifier));
                log.debug("Submitted pk");
                response.close();
            }
        });
    }
}