package ist.alchm.smthsVanish.paper.disguise;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;

/** Looks up a premium account's signed skin from Mojang. Empty when the account does not exist. */
@NullMarked
final class SkinFetcher {
    record Skin(String value, String signature) {}

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    CompletableFuture<Optional<Skin>> fetch(String name) {
        return get("https://api.mojang.com/users/profiles/minecraft/" + name).thenCompose(profile -> {
            if (profile.isEmpty()) return CompletableFuture.completedFuture(Optional.<Skin>empty());
            String id = profile.get().get("id").getAsString();
            return get("https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false")
                    .thenApply(session -> session.flatMap(SkinFetcher::textures));
        }).exceptionally(e -> Optional.empty());
    }

    private static Optional<Skin> textures(JsonObject session) {
        for (var element : session.getAsJsonArray("properties")) {
            JsonObject property = element.getAsJsonObject();
            if ("textures".equals(property.get("name").getAsString()) && property.has("signature")) {
                return Optional.of(new Skin(property.get("value").getAsString(), property.get("signature").getAsString()));
            }
        }
        return Optional.empty();
    }

    private CompletableFuture<Optional<JsonObject>> get(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response ->
                response.statusCode() == 200
                        ? Optional.of(JsonParser.parseString(response.body()).getAsJsonObject())
                        : Optional.empty());
    }
}
